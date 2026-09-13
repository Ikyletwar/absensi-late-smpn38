package com.osis.smkn1malteng.absensilate.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class SyncClient(private val context: Context) {

    companion object {
        private const val TAG = "SyncClient"
        private const val CONNECTION_TIMEOUT_SECONDS = 30L
        private const val READ_TIMEOUT_SECONDS = 30L
    }

    // 🔥 Network yang di-bind ke proses — harus dilepas setelah sync selesai,
    //    kalau tidak, trafik app (termasuk kirim Telegram) tetap diroute
    //    ke network hotspot yang sudah mati.
    @Volatile
    private var boundNetwork: Network? = null

    private fun unbindNetwork() {
        try {
            if (boundNetwork != null) {
                val connectivityManager = context.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.bindProcessToNetwork(null)
                Log.d(TAG, "Process unbound from sync network")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind network: ${e.message}")
        } finally {
            boundNetwork = null
        }
    }

    data class QrHandshake(
        val ssid: String,
        val password: String,
        val serverUrl: String
    )

    sealed class SyncResult {
        data class Success(
            val students: List<com.osis.smkn1malteng.absensilate.data.local.StudentEntity>,
            val source: String
        ) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object Cancelled : SyncResult()
    }

    /**
     * Parse QR content yang berisi gabungan WIFI + URL
     * Format: "WIFI:T:WPA;S:SSID;P:PASSWORD;;||http://IP:PORT/sync?sid=..."
     */
    fun parseCombinedQr(fullContent: String): QrHandshake? {
        val parts = fullContent.split("||")
        if (parts.size != 2) {
            Log.w(TAG, "Invalid combined QR format, parts: ${parts.size}")
            return null
        }

        val wifiPart = parts[0]
        val urlPart = parts[1]

        val ssid = extractWifiParam(wifiPart, "S:")
        val password = extractWifiParam(wifiPart, "P:")

        if (ssid == null || password == null) {
            Log.w(TAG, "Failed to extract SSID or password from: $wifiPart")
            return null
        }

        if (!urlPart.startsWith("http://") && !urlPart.startsWith("https://")) {
            Log.w(TAG, "Invalid URL: $urlPart")
            return null
        }

        return QrHandshake(
            ssid = ssid,
            password = password,
            serverUrl = urlPart
        )
    }

    private fun extractWifiParam(wifiString: String, param: String): String? {
        val regex = Regex("$param([^;]+)")
        return regex.find(wifiString)?.groupValues?.get(1)
    }

    /**
     * Connect ke Wi-Fi menggunakan WifiNetworkSpecifier.
     */
    suspend fun connectToWifi(ssid: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connectivityManager = context.applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()

                val deferred = CompletableDeferred<Boolean>()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(TAG, "Wi-Fi connected: $ssid")
                        boundNetwork = network
                        connectivityManager.bindProcessToNetwork(network)
                        deferred.complete(true)
                    }

                    override fun onUnavailable() {
                        Log.w(TAG, "Wi-Fi network unavailable: $ssid")
                        deferred.complete(false)
                    }

                    override fun onLost(network: Network) {
                        Log.d(TAG, "Wi-Fi lost: $ssid")
                    }
                }

                connectivityManager.requestNetwork(networkRequest, callback)

                val result = try {
                    // 🔥 Timeout agar coroutine tidak hang selamanya kalau hotspot
                    //    tidak kunjung tersedia
                    withTimeoutOrNull(TimeUnit.SECONDS.toMillis(CONNECTION_TIMEOUT_SECONDS)) {
                        deferred.await()
                    } ?: false.also { Log.w(TAG, "Wi-Fi connection timed out") }
                } catch (e: Exception) {
                    Log.e(TAG, "Wi-Fi connection error: ${e.message}", e)
                    false
                }

                connectivityManager.unregisterNetworkCallback(callback)
                result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Wi-Fi: ${e.message}", e)
                false
            }
        }
    }

    suspend fun fetchData(serverUrl: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = TimeUnit.SECONDS.toMillis(CONNECTION_TIMEOUT_SECONDS).toInt()
                    readTimeout = TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SECONDS).toInt()
                    setRequestProperty("Accept", "text/plain")
                }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP error: $responseCode")
                    val errorStream = connection.errorStream
                    val errorMessage = if (errorStream != null) {
                        BufferedReader(InputStreamReader(errorStream)).use { reader ->
                            reader.readText()
                        }
                    } else {
                        "HTTP $responseCode"
                    }
                    connection.disconnect()
                    return@withContext null
                }

                val result = connection.inputStream.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.readText()
                    }
                }

                connection.disconnect()
                Log.d(TAG, "Fetched ${result.length} characters")
                result

            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch data: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Auto mode: parse QR combined, connect, fetch, deserialize.
     */
    suspend fun sync(qrContent: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                val handshake = parseCombinedQr(qrContent)
                if (handshake == null) {
                    // Fallback ke legacy
                    if (com.osis.smkn1malteng.absensilate.sync.CompactSerializer.isLegacyFormat(qrContent)) {
                        val students = com.osis.smkn1malteng.absensilate.sync.QrSyncEngine.decodeStudentsLegacy(qrContent)
                        if (students.isNotEmpty()) {
                            return@withContext SyncResult.Success(students, "legacy")
                        } else {
                            return@withContext SyncResult.Error("Legacy QR data is empty or invalid")
                        }
                    }
                    return@withContext SyncResult.Error("Invalid QR format")
                }

                val connected = connectToWifi(handshake.ssid, handshake.password)
                if (!connected) {
                    return@withContext SyncResult.Error("Failed to connect to Wi-Fi hotspot")
                }

                val encodedData = fetchData(handshake.serverUrl)
                if (encodedData == null) {
                    return@withContext SyncResult.Error("Failed to fetch data from server")
                }

                val students = com.osis.smkn1malteng.absensilate.sync.CompactSerializer.deserialize(encodedData)
                if (students.isEmpty()) {
                    return@withContext SyncResult.Error("No student data received or data is corrupted")
                }

                SyncResult.Success(students, "handshake_auto")

            } catch (e: CancellationException) {
                SyncResult.Cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}", e)
                SyncResult.Error(e.message ?: "Unknown error")
            } finally {
                // 🔥 Lepas bind network agar trafik normal (internet) kembali
                unbindNetwork()
            }
        }
    }

    /**
     * Manual mode: given SSID & password, use them.
     * This overload is used for legacy handshake or fallback.
     */
    suspend fun sync(qrContent: String, ssid: String, password: String): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // Try to extract server URL from QR if it's combined, else assume QR is just URL
                val handshake = parseCombinedQr(qrContent)
                val serverUrl = if (handshake != null) {
                    handshake.serverUrl
                } else {
                    // QR might be just URL
                    if (qrContent.startsWith("http://") || qrContent.startsWith("https://")) {
                        qrContent
                    } else {
                        return@withContext SyncResult.Error("Invalid QR format for manual mode")
                    }
                }

                // Use provided credentials
                val connected = connectToWifi(ssid, password)
                if (!connected) {
                    return@withContext SyncResult.Error("Failed to connect to Wi-Fi hotspot")
                }

                val encodedData = fetchData(serverUrl)
                if (encodedData == null) {
                    return@withContext SyncResult.Error("Failed to fetch data from server")
                }

                val students = com.osis.smkn1malteng.absensilate.sync.CompactSerializer.deserialize(encodedData)
                if (students.isEmpty()) {
                    return@withContext SyncResult.Error("No student data received or data is corrupted")
                }

                SyncResult.Success(students, "handshake_manual")

            } catch (e: CancellationException) {
                SyncResult.Cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}", e)
                SyncResult.Error(e.message ?: "Unknown error")
            } finally {
                // 🔥 Lepas bind network agar trafik normal (internet) kembali
                unbindNetwork()
            }
        }
    }
}
