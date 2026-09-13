package com.osis.smkn1malteng.absensilate.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecapExporter {

    private const val PAGE_WIDTH = 595f   // A4 width in points
    private const val PAGE_HEIGHT = 842f  // A4 height in points
    private const val MARGIN = 40f
    private const val LINE_HEIGHT = 18f

    // ============================================
    // EXPORT CSV
    // ============================================
    fun exportRecapCsv(
        context: Context,
        students: List<StudentEntity>
    ): Uri? {
        if (students.isEmpty()) return null

        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale("id", "ID"))
        val fileName = "Rekap_Pelanggaran_${System.currentTimeMillis()}.csv"

        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                @Suppress("DEPRECATION")
                val file = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                Uri.fromFile(file)
            }

            uri?.let { outputUri ->
                context.contentResolver.openOutputStream(outputUri)?.bufferedWriter()?.use { writer ->
                    // Header
                    writer.write("No,Nama,Kelas,SubKelas,NoHP,NoHP_WaliKelas,PelanggaranMingguIni,TotalPelanggaran,RiwayatTimestamp\n")

                    // Data rows
                    students.forEachIndexed { index, student ->
                        val timestamps = student.timestamps.joinToString(";") { dateFormat.format(Date(it)) }
                        writer.write(
                            "${index + 1}," +
                            "\"${escapeCsv(student.name)}\"," +
                            "${student.kelas.display}," +
                            "${student.subKelas.display}," +
                            "${student.phone}," +
                            "${student.waliKelasPhone ?: ""}," +
                            "${student.violationCount}," +
                            "${student.timestamps.size}," +
                            "\"${escapeCsv(timestamps)}\"\n"
                        )
                    }
                    writer.flush()
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Escape nilai untuk field CSV yang di-quote (standar RFC 4180):
     * tanda kutip digandakan agar tidak merusak kolom di Excel/Sheets.
     */
    private fun escapeCsv(value: String): String = value.replace("\"", "\"\"")

    // ============================================
    // EXPORT PDF
    // ============================================
    fun exportRecapPdf(
        context: Context,
        students: List<StudentEntity>
    ): Uri? {
        if (students.isEmpty()) return null

        val pdfDocument = PdfDocument()
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy HH:mm", Locale("id", "ID"))
        val todayFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

        var pageNumber = 1
        var yPos = MARGIN
        var currentPage: PdfDocument.Page? = null

        fun newPage(): Canvas {
            currentPage?.let { pdfDocument.finishPage(it) }
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber).create()
            currentPage = pdfDocument.startPage(pageInfo)
            pageNumber++
            yPos = MARGIN
            return currentPage!!.canvas
        }

        var canvas = newPage()

        val headerPaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val titlePaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 10f }
        val smallPaint = Paint().apply { textSize = 8f; color = android.graphics.Color.GRAY }
        val dividerPaint = Paint().apply { strokeWidth = 1f; color = android.graphics.Color.LTGRAY }

        // === HEADER SEKOLAH ===
        canvas.drawText("SMP NEGERI 38 MALUKU TENGAH", MARGIN, yPos + 16f, headerPaint)
        yPos += 24f
        canvas.drawText("Rekap Pelanggaran Siswa", MARGIN, yPos + 12f, titlePaint)
        yPos += 20f
        canvas.drawText("Tanggal Cetak: ${todayFormat.format(Date())}", MARGIN, yPos + 10f, bodyPaint)
        yPos += 16f
        canvas.drawText("Jumlah Siswa: ${students.size}", MARGIN, yPos + 10f, bodyPaint)
        yPos += 20f
        canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, dividerPaint)
        yPos += 12f

        // === DATA PER SISWA ===
        for ((index, student) in students.withIndex()) {
            val estimatedBlockHeight = LINE_HEIGHT * (4 + student.timestamps.size.coerceAtMost(10).toFloat())

            if (yPos + estimatedBlockHeight > PAGE_HEIGHT - MARGIN) {
                canvas = newPage()
            }

            // Nomor & Nama
            canvas.drawText("${index + 1}. ${student.name}", MARGIN, yPos + 10f, titlePaint)
            yPos += LINE_HEIGHT

            // Kelas
            canvas.drawText("   Kelas: ${student.kelas.display}-${student.subKelas.display}", MARGIN, yPos + 10f, bodyPaint)
            yPos += LINE_HEIGHT

            // Pelanggaran
            canvas.drawText("   Pelanggaran Minggu Ini: ${student.violationCount}x | Total: ${student.timestamps.size}x", MARGIN, yPos + 10f, bodyPaint)
            yPos += LINE_HEIGHT

            // Riwayat Timestamp (max 10 terakhir)
            if (student.timestamps.isNotEmpty()) {
                canvas.drawText("   Riwayat:", MARGIN, yPos + 8f, smallPaint)
                yPos += LINE_HEIGHT * 0.8f
                student.timestamps.reversed().take(10).forEach { ts ->
                    if (yPos + LINE_HEIGHT > PAGE_HEIGHT - MARGIN) {
                        canvas = newPage()
                    }
                    canvas.drawText("     • ${dateFormat.format(Date(ts))}", MARGIN, yPos + 8f, smallPaint)
                    yPos += LINE_HEIGHT * 0.8f
                }
                if (student.timestamps.size > 10) {
                    canvas.drawText("     ... dan ${student.timestamps.size - 10} catatan lainnya", MARGIN, yPos + 8f, smallPaint)
                    yPos += LINE_HEIGHT * 0.8f
                }
            }

            yPos += LINE_HEIGHT * 0.5f

            // Garis pemisah antar siswa
            if (index < students.size - 1) {
                if (yPos + 8f > PAGE_HEIGHT - MARGIN) {
                    canvas = newPage()
                }
                canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, dividerPaint)
                yPos += 12f
            }
        }

        // === FOOTER ===
        if (yPos + LINE_HEIGHT * 2 > PAGE_HEIGHT - MARGIN) {
            canvas = newPage()
        }
        yPos += LINE_HEIGHT
        canvas.drawLine(MARGIN, yPos, PAGE_WIDTH - MARGIN, yPos, dividerPaint)
        yPos += 12f
        canvas.drawText("Dicetak oleh: Aplikasi Absensi Late SMPN 38 Maluku Tengah", MARGIN, yPos + 8f, smallPaint)

        // Finish last page
        currentPage?.let { pdfDocument.finishPage(it) }

        // Simpan ke file
        val fileName = "Rekap_Pelanggaran_${System.currentTimeMillis()}.pdf"

        return try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/")
                }
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                @Suppress("DEPRECATION")
                val file = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                    fileName
                )
                Uri.fromFile(file)
            }

            uri?.let { outputUri ->
                context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            pdfDocument.close()
        }
    }
}
