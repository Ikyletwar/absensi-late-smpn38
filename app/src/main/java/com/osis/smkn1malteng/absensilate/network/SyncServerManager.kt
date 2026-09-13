package com.osis.smkn1malteng.absensilate.network

import android.content.Context
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

class SyncServerManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SyncServerManager"
        private const val SERVER_PORT = 8080
        private const val SYNC_PATH = "/sync"
    }

    sealed class ServerState {
        object Idle : ServerState()
        object Starting : ServerState()
        data class Active(
            val ssid: String,
            val password: String,
            val ipAddress: String,
            val port: Int,
            val sessionId: String,
            val qrContent: String  // 🔥 QR content lengkap (WIFI + URL)
        ) : ServerState()
        object Stopping : ServerState()
        data class Error(val message: String) : ServerState()
    }

    private val _state = MutableStateFlow<ServerState>(ServerState.Idle)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var hotspotReservation: LocalOnlyHotspotReservation? = null
    private var server: SyncHttpServer? = null
    private var serverJob: Job? = null
    private var sessionId: String = ""

    suspend fun start(students: () -> List<com.osis.smkn1malteng.absensilate.data.local.StudentEntity>): Result<ServerState.Active> {
        return withContext(Dispatchers.IO) {
            try {
                _state.value = ServerState.Starting

                val reservation = startHotspot()
                hotspotReservation = reservation

                val softApConfig = reservation.softApConfiguration
                val ssid = softApConfig?.ssid ?: "Unknown"
                val password = softApConfig?.passphrase ?: ""

                val ipAddress = getLocalIpAddress() ?: "127.0.0.1"
                sessionId = UUID.randomUUID().toString()

                Log.d(TAG, "Hotspot active: SSID=$ssid, IP=$ipAddress")

                server = SyncHttpServer(SERVER_PORT, students, sessionId)
                server?.start()

                Log.d(TAG, "HTTP server started on port $SERVER_PORT")

                // 🔥 Buat QR content gabungan: WIFI + URL
                val serverUrl = "http://${ipAddress}:${SERVER_PORT}${SYNC_PATH}?sid=${sessionId}"
                // Format standar Wi-Fi QR Code
                val wifiPart = "WIFI:T:WPA;S:${ssid};P:${password};;"
                // Gabungkan dengan pemisah "||"
                val qrContent = "$wifiPart||$serverUrl"

                val activeState = ServerState.Active(
                    ssid = ssid,
                    password = password,
                    ipAddress = ipAddress,
                    port = SERVER_PORT,
                    sessionId = sessionId,
                    qrContent = qrContent
                )

                _state.value = activeState
                Result.success(activeState)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server: ${e.message}", e)
                _state.value = ServerState.Error(e.message ?: "Unknown error")
                cleanup()
                Result.failure(e)
            }
        }
    }

    private suspend fun startHotspot(): LocalOnlyHotspotReservation {
        return withContext(Dispatchers.Main) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val deferred = CompletableDeferred<LocalOnlyHotspotReservation>()

            val callback = object : LocalOnlyHotspotCallback() {
                override fun onStarted(reservation: LocalOnlyHotspotReservation) {
                    Log.d(TAG, "LocalOnlyHotspot started")
                    deferred.complete(reservation)
                }

                override fun onStopped() {
                    Log.d(TAG, "LocalOnlyHotspot stopped")
                    if (!deferred.isCompleted) {
                        deferred.completeExceptionally(IOException("Hotspot stopped unexpectedly"))
                    }
                }

                override fun onFailed(reason: Int) {
                    Log.e(TAG, "LocalOnlyHotspot failed: reason=$reason")
                    val message = when (reason) {
                        LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No Wi-Fi channel available"
                        LocalOnlyHotspotCallback.ERROR_GENERIC -> "Generic error"
                        LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "Incompatible mode"
                        else -> "Unknown error (code $reason)"
                    }
                    deferred.completeExceptionally(IOException(message))
                }
            }

            wifiManager.startLocalOnlyHotspot(callback, android.os.Handler(context.mainLooper))

            try {
                deferred.await()
            } catch (e: CancellationException) {
                deferred.await().close()
                throw e
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        val hostAddress = address.hostAddress ?: continue
                        if (!hostAddress.contains(":")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get IP address: ${e.message}", e)
        }
        return null
    }

    fun stop() {
        scope.launch(Dispatchers.IO) {
            _state.value = ServerState.Stopping
            cleanup()
            _state.value = ServerState.Idle
        }
    }

    private fun cleanup() {
        server?.stop()
        server = null
        serverJob?.cancel()
        serverJob = null

        try {
            hotspotReservation?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing hotspot: ${e.message}", e)
        }
        hotspotReservation = null
        sessionId = ""
    }

    fun getServerUrl(): String? {
        return when (val s = _state.value) {
            is ServerState.Active -> "http://${s.ipAddress}:${s.port}${SYNC_PATH}?sid=${s.sessionId}"
            else -> null
        }
    }

    fun getCredentials(): Pair<String, String>? {
        return when (val s = _state.value) {
            is ServerState.Active -> Pair(s.ssid, s.password)
            else -> null
        }
    }

    fun getQrContent(): String? {
        return when (val s = _state.value) {
            is ServerState.Active -> s.qrContent
            else -> null
        }
    }

    fun isActive(): Boolean {
        return _state.value is ServerState.Active
    }
}

/**
 * SyncHttpServer — NanoHTTPD server that serves compressed student data.
 */
class SyncHttpServer(
    private val port: Int,
    private val studentsProvider: () -> List<com.osis.smkn1malteng.absensilate.data.local.StudentEntity>,
    private val expectedSessionId: String
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "SyncHttpServer"
    }

    @Throws(IOException::class)
    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method != NanoHTTPD.Method.GET) {
                return newFixedLengthResponse(
                    Status.METHOD_NOT_ALLOWED,
                    "text/plain",
                    "Method not allowed"
                )
            }

            val uri = session.uri ?: ""
            if (!uri.startsWith("/sync")) {
                return newFixedLengthResponse(
                    Status.NOT_FOUND,
                    "text/plain",
                    "Not found"
                )
            }

            val params = session.parameters
            val sid = params["sid"]?.firstOrNull() ?: ""
            if (sid != expectedSessionId) {
                Log.w(TAG, "Invalid session ID: $sid")
                return newFixedLengthResponse(
                    Status.UNAUTHORIZED,
                    "text/plain",
                    "Invalid session"
                )
            }

            val students = studentsProvider()
            val serialized = com.osis.smkn1malteng.absensilate.sync.CompactSerializer.serialize(students)

            Log.d(TAG, "Serving ${students.size} students, payload size: ${serialized.length} chars")

            newFixedLengthResponse(
                Status.OK,
                "text/plain",
                serialized
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error serving request: ${e.message}", e)
            newFixedLengthResponse(
                Status.INTERNAL_ERROR,
                "text/plain",
                "Server error: ${e.message}"
            )
        }
    }
}
