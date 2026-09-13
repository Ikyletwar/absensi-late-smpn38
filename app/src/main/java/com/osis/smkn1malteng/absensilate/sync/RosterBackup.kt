package com.osis.smkn1malteng.absensilate.sync

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 🔥 Backup / Restore NAMA SISWA (roster saja, TANPA pelanggaran).
 *
 * Beda dengan CsvEngine:
 * - Roster hanya berisi data identitas siswa (nama, kelas, subKelas, No HP, No HP wali).
 * - Restore bersifat GANTI TOTAL (replace), sedangkan import CSV lama bersifat tambah (append).
 * - Header file divalidasi agar file CSV lama (9 kolom) tidak salah terbaca sebagai roster.
 */
object RosterBackup {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
    private val header = "Nama,Kelas,SubKelas,NoHP,NoHP_WaliKelas\n"

    fun writeRosterToUri(
        contentResolver: ContentResolver,
        uri: Uri,
        students: List<StudentEntity>
    ) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(header)
                students.forEach { student ->
                    writer.write(
                        "${escape(student.name)}," +
                            "${student.kelas.display}," +
                            "${student.subKelas.display}," +
                            "${escape(student.phone)}," +
                            "${escape(student.waliKelasPhone ?: "")}\n"
                    )
                }
                writer.flush()
            }
        }
    }

    fun parseRosterFromUri(contentResolver: ContentResolver, uri: Uri): List<StudentEntity> {
        val students = mutableListOf<StudentEntity>()
        val lines = contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readLines()
        } ?: return students

        if (lines.isEmpty()) return students

        // 🔥 VALIDASI HEADER — file yang bukan roster (mis. CSV export lama 9 kolom) DITOLAK.
        val head = parseCsvLine(lines[0])
        val isRosterHeader = head.size >= 2 &&
            head[0].trim().equals("Nama", ignoreCase = true) &&
            head[1].trim().equals("Kelas", ignoreCase = true)
        if (!isRosterHeader) {
            Log.w("RosterBackup", "Header tidak dikenal — bukan file backup roster")
            return students
        }

        for ((index, line) in lines.withIndex()) {
            if (index == 0) continue
            if (line.isBlank()) continue

            try {
                val parts = parseCsvLine(line)
                if (parts.size < 2) {
                    Log.w("RosterBackup", "Line ${index + 1} tidak lengkap (${parts.size} kolom)")
                    continue
                }

                val name = parts[0].trim()
                if (name.isEmpty()) continue

                val kelasStr = parts.getOrNull(1)?.trim() ?: ""
                val subKelasStr = parts.getOrNull(2)?.trim() ?: ""
                val phone = parts.getOrNull(3)?.trim() ?: ""
                val waliKelasPhone = parts.getOrNull(4)?.trim()?.ifEmpty { null }

                // 🔥 STRICT enum matching — nilai tidak dikenal → baris dilewati dengan log
                val kelas = SmpClass.values().firstOrNull {
                    it.display == kelasStr || it.name == kelasStr
                }
                if (kelas == null) {
                    Log.w("RosterBackup", "Line ${index + 1}: kelas tidak dikenali '$kelasStr' — baris dilewati")
                    continue
                }
                val subKelas = SmpSubClass.values().firstOrNull {
                    it.display == subKelasStr || it.name == subKelasStr
                }
                if (subKelas == null) {
                    Log.w("RosterBackup", "Line ${index + 1}: subKelas tidak dikenali '$subKelasStr' — baris dilewati")
                    continue
                }

                students.add(
                    StudentEntity(
                        id = 0,
                        uuid = UUID.randomUUID().toString(),
                        name = name,
                        phone = phone,
                        waliKelasPhone = waliKelasPhone,
                        kelas = kelas,
                        subKelas = subKelas,
                        violationCount = 0,
                        timestamps = emptyList()
                    )
                )
            } catch (e: Exception) {
                Log.e("RosterBackup", "Error parsing line ${index + 1}: ${e.message}")
            }
        }
        return students
    }

    fun suggestedBackupFileName(): String {
        return "Backup_Nama_Siswa_${dateFormat.format(Date())}.csv"
    }

    // Escape field yang mengandung koma/kutip (sesuai aturan CSV)
    private fun escape(value: String): String {
        return if (value.contains(',') || value.contains('"')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    // Parse satu baris CSV, handle field berisi koma (dalam quotes)
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i += 2
                    } else {
                        inQuotes = !inQuotes
                        i++
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                    i++
                }
                else -> {
                    current.append(char)
                    i++
                }
            }
        }
        result.add(current.toString().trim())
        return result
    }
}