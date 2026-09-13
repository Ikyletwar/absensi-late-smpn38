package com.osis.smkn1malteng.absensilate.network

import android.content.Context
import android.os.Build
import com.google.gson.Gson
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.sync.CsvEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPOutputStream
import javax.net.ssl.SSLException

/**
 * Hasil kirim - bawa detail alasan gagal, gak bergantung Logcat sama sekali.
 * Detail bisa langsung ditampilin ke Toast/Dialog buat debug tanpa perlu adb.
 */
sealed class SendResult {
    object Success : SendResult()
    data class Failure(val reason: String, val detail: String) : SendResult()
}

object TelegramSender {
    // 🔥 Token & chat_id diambil dari local.properties via BuildConfig
    //    (tidak lagi hard-coded di source)
    private const val BOT_TOKEN = com.smpn38malteng.absensilate.BuildConfig.TELEGRAM_BOT_TOKEN
    private const val CHAT_ID = com.smpn38malteng.absensilate.BuildConfig.TELEGRAM_CHAT_ID

    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale("id", "ID"))

    // ============================================================
    // TEST MINIMAL - buat isolasi masalah koneksi, gak ada gzip/file/markdown
    // Pakai ini dulu buat mastiin koneksi ke Telegram dari HP ini jalan.
    // ============================================================
    suspend fun testMinimal(): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage?chat_id=$CHAT_ID&text=Test+dari+HP+langsung")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val code = connection.responseCode
                val body = if (code == 200) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "(no body)"
                }

                Pair(code == 200, "HTTP $code: $body")
            } catch (e: Exception) {
                Pair(false, "${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    // ============================================================
    // VERSI LENGKAP - dipakai MainViewModel.sendAllStudentsToServer()
    // ============================================================
    suspend fun sendAllStudents(context: Context, students: List<StudentEntity>): SendResult {
        return withContext(Dispatchers.IO) {
            var gzFile: File? = null
            var csvFile: File? = null
            try {
                val json = gson.toJson(students)
                val compressed = gzipCompress(json)
                val timeSuffix = System.currentTimeMillis()

                val gzFileName = "students_$timeSuffix.json.gz"
                gzFile = File(context.cacheDir, gzFileName)
                FileOutputStream(gzFile).use { fos -> fos.write(compressed) }

                val csvFileName = "students_$timeSuffix.csv"
                csvFile = File(context.cacheDir, csvFileName)
                csvFile.writeText(CsvEngine.buildCsv(students))

                val message = buildInfoMessage(
                    context,
                    students,
                    compressed.size,
                    gzFileName,
                    csvFile.length(),
                    csvFileName
                )
                val messageResult = sendMessage(message)
                if (messageResult is SendResult.Failure) return@withContext messageResult

                val gzResult = sendDocument(gzFile)
                if (gzResult is SendResult.Failure) return@withContext gzResult

                val csvResult = sendDocument(csvFile)
                if (csvResult is SendResult.Failure) return@withContext csvResult

                SendResult.Success
            } catch (e: UnknownHostException) {
                SendResult.Failure("Gak ada koneksi internet / DNS gagal", e.message ?: "UnknownHostException")
            } catch (e: SocketTimeoutException) {
                SendResult.Failure("Timeout koneksi", e.message ?: "SocketTimeoutException")
            } catch (e: SSLException) {
                SendResult.Failure("SSL handshake error", e.message ?: "SSLException")
            } catch (e: SecurityException) {
                SendResult.Failure("SecurityException (permission/firewall)", e.message ?: "SecurityException")
            } catch (e: Exception) {
                SendResult.Failure(e.javaClass.simpleName, e.message ?: "(no message)")
            } finally {
                gzFile?.delete()
                csvFile?.delete()
            }
        }
    }

    private fun gzipCompress(input: String): ByteArray {
        return ByteArrayOutputStream().use { baos ->
            GZIPOutputStream(baos).use { gzip -> gzip.write(input.toByteArray(Charsets.UTF_8)) }
            baos.toByteArray()
        }
    }

    private fun buildInfoMessage(
        context: Context,
        students: List<StudentEntity>,
        gzSizeBytes: Int,
        gzFileName: String,
        csvSizeBytes: Long,
        csvFileName: String
    ): String {
        val deviceName = Build.MANUFACTURER + " " + Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        val appName = context.applicationInfo.loadLabel(context.packageManager).toString()
        val packageName = context.packageName
        val time = dateFormat.format(Date())
        val totalViolations = students.sumOf { it.violationCount }
        val totalTimestamps = students.sumOf { it.timestamps.size }

        return """
📤 DATA SISWA DIKIRIM KE SERVER

📱 Pengirim: $deviceName (Android $androidVersion)
📦 Aplikasi: $appName ($packageName)
🕐 Waktu Kirim: $time
📊 Jumlah Siswa: ${students.size} siswa
⚠️ Total Pelanggaran Minggu Ini: $totalViolations
📋 Total Catatan Pelanggaran: $totalTimestamps
🗜️ File JSON.GZ: $gzFileName (${formatSize(gzSizeBytes.toLong())})
📄 File CSV: $csvFileName (${formatSize(csvSizeBytes)})

✅ Data dikirim sebagai JSON.GZ + CSV.
        """.trimIndent()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024L * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024L * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024L -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }

    private fun readErrorBody(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "(no error body)"
        } catch (e: Exception) {
            "gagal baca error stream: ${e.message}"
        }
    }

    private fun sendMessage(text: String): SendResult {
        val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        val payload = """
            {
                "chat_id": "$CHAT_ID",
                "text": ${escapeJson(text)}
            }
        """.trimIndent()

        connection.outputStream.use { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        return if (responseCode == HttpURLConnection.HTTP_OK) {
            SendResult.Success
        } else {
            SendResult.Failure("sendMessage HTTP $responseCode", readErrorBody(connection))
        }
    }

    private fun sendDocument(file: File): SendResult {
        val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendDocument")
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=*****")
        }

        val boundary = "*****"
        val lineFeed = "\r\n"
        val twoHyphens = "--"

        connection.outputStream.use { outputStream ->
            val writer = DataOutputStream(outputStream)

            writer.writeBytes(twoHyphens + boundary + lineFeed)
            writer.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineFeed)
            writer.writeBytes(lineFeed)
            writer.writeBytes(CHAT_ID + lineFeed)

            writer.writeBytes(twoHyphens + boundary + lineFeed)
            writer.writeBytes("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"" + lineFeed)
            writer.writeBytes("Content-Type: application/gzip" + lineFeed)
            writer.writeBytes(lineFeed)

            file.inputStream().use { fis ->
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    writer.write(buffer, 0, bytesRead)
                }
            }
            writer.writeBytes(lineFeed)
            writer.writeBytes(twoHyphens + boundary + twoHyphens + lineFeed)
            writer.flush()
        }

        val responseCode = connection.responseCode
        return if (responseCode == HttpURLConnection.HTTP_OK) {
            SendResult.Success
        } else {
            SendResult.Failure("sendDocument HTTP $responseCode", readErrorBody(connection))
        }
    }

    private fun escapeJson(text: String): String {
        return "\"" + text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
    }
}