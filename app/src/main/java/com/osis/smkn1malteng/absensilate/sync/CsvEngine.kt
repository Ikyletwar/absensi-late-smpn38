package com.osis.smkn1malteng.absensilate.sync

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvEngine {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val header = "ID,Nama,NoHP,NoHP_WaliKelas,Kelas,SubKelas,ViolationCount,TotalPelanggaran,Timestamps\n"

fun writeCsvToUri(
        contentResolver: ContentResolver,
        uri: Uri,
        students: List<StudentEntity>,
        weekNumber: Int
    ) {
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { writer ->
                writer.write(buildCsv(students, weekNumber))
                writer.flush()
            }
        }
    }

    fun buildCsv(students: List<StudentEntity>, weekNumber: Int = 0): String {
        val sb = StringBuilder(header)
        students.forEach { student ->
            val timestamps = student.timestamps.joinToString(";") { dateFormat.format(Date(it)) }
            sb.append(
                "${student.id}," +
                "${student.name}," +
                "${student.phone}," +
                "${student.waliKelasPhone ?: ""}," +
                "${student.kelas.display}," +
                "${student.subKelas.display}," +
                "${student.violationCount}," +
                "${student.timestamps.size}," +
                "$timestamps\n"
            )
        }
        return sb.toString()
    }

    fun parseCsvFromUri(contentResolver: ContentResolver, uri: Uri): List<StudentEntity> {
        val students = mutableListOf<StudentEntity>()
        var lineNumber = 0
        contentResolver.openInputStream(uri)?.use { inputStream ->
            InputStreamReader(inputStream).buffered().use { reader ->
                reader.forEachLine { line ->
                    lineNumber++
                    if (lineNumber == 1) {
                        // Skip header
                        return@forEachLine
                    }
                    if (line.isBlank()) return@forEachLine

                    try {
                        val parts = parseCsvLine(line)
                        if (parts.size < 9) {
                            Log.w("CsvEngine", "Line $lineNumber has ${parts.size} columns, expected 9")
                            return@forEachLine
                        }

                        val id = parts[0].toLongOrNull() ?: 0L
                        val name = parts[1].trim()
                        val phone = parts[2].trim()
                        val waliKelasPhone = parts[3].trim().ifEmpty { null }
                        val kelasStr = parts[4].trim()
                        val subKelasStr = parts[5].trim()
                        val violationCount = parts[6].toIntOrNull() ?: 0
                        // parts[7] is TotalPelanggaran, we don't use it

                        val timestamps = if (parts.size > 8 && parts[8].isNotEmpty()) {
                            parts[8].split(";").mapNotNull { timestampStr ->
                                try {
                                    dateFormat.parse(timestampStr.trim())?.time
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        } else emptyList()

                        // 🔥 STRICT enum matching — nilai tidak dikenal (mis. CSV schema
                        //    SMK lama: "X"/"TJKT 1") → baris di-skip dengan log,
                        //    BUKAN di-fabricate diam-diam ke VII/SUB_1.
                        val kelas = SmpClass.values().firstOrNull {
                            it.display == kelasStr || it.name == kelasStr
                        }
                        if (kelas == null) {
                            Log.w("CsvEngine", "Line $lineNumber: kelas tidak dikenali '$kelasStr' — baris dilewati")
                            return@forEachLine
                        }
                        val subKelas = SmpSubClass.values().firstOrNull {
                            it.display == subKelasStr || it.name == subKelasStr
                        }
                        if (subKelas == null) {
                            Log.w("CsvEngine", "Line $lineNumber: subKelas tidak dikenali '$subKelasStr' — baris dilewati")
                            return@forEachLine
                        }

                        students.add(
                            StudentEntity(
                                id = id,
                                name = name,
                                phone = phone,
                                waliKelasPhone = waliKelasPhone,
                                kelas = kelas,
                                subKelas = subKelas,
                                violationCount = violationCount,
                                timestamps = timestamps
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("CsvEngine", "Error parsing line $lineNumber: ${e.message}")
                        // Skip this line but continue
                    }
                }
            }
        }
        return students
    }

    // Parse CSV line dengan handle field yang mengandung koma (dalam quotes)
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