package com.osis.smkn1malteng.absensilate.sync

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.osis.smkn1malteng.absensilate.data.local.StudentEntity
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * QrSyncEngine — Handles QR code encoding/decoding with backward compatibility.
 *
 * Two formats supported:
 * 1. NEW (preferred): URL handshake via CompactSerializer
 * 2. LEGACY: Base64 JSON (for backward compatibility)
 *
 * Merge strategy: Primary key is UUID. If UUID doesn't exist, fallback to name.
 */
object QrSyncEngine {
    private val gson = Gson()

    // Data Transfer Object untuk QR (hanya field dasar) — LEGACY FORMAT
    private data class StudentQrData(
        val name: String,
        val phone: String,
        val waliKelasPhone: String?,
        val kelas: String,          // display name, misal "7"
        val subKelas: String,       // display name, misal "Sains"
        val violationCount: Int,
        val timestamps: List<Long>
    )

    /**
     * Encode list siswa ke Base64 JSON — LEGACY FORMAT.
     * @deprecated Gunakan CompactSerializer untuk format baru.
     */
    @Deprecated("Use CompactSerializer for new format")
    suspend fun encodeStudents(students: List<StudentEntity>): String = withContext(Dispatchers.IO) {
        val qrData = students.map { student ->
            StudentQrData(
                name = student.name,
                phone = student.phone,
                waliKelasPhone = student.waliKelasPhone,
                kelas = student.kelas.display,
                subKelas = student.subKelas.display,
                violationCount = student.violationCount,
                timestamps = student.timestamps
            )
        }
        val json = gson.toJson(qrData)
        Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    /**
     * Decode Base64 JSON ke List<StudentEntity> — LEGACY FORMAT.
     * Used for backward compatibility with old QR codes.
     */
    suspend fun decodeStudentsLegacy(encoded: String): List<StudentEntity> = withContext(Dispatchers.IO) {
        try {
            val json = String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            val type = object : TypeToken<List<StudentQrData>>() {}.type
            val qrDataList: List<StudentQrData> = gson.fromJson(json, type)

            qrDataList.mapNotNull { qrData ->
                try {
                    // 🔥 STRICT enum matching — data schema lama (kelas "X"/jurusan "TJKT 1")
                    // TIDAK cocok → entry di-skip, bukan di-fabricate ke VII/SUB_1.
                    // Toleran: terima NAME ("VII") maupun DISPLAY lama ("7") / baru ("VII").
                    val kelas = SmpClass.values().firstOrNull { it.name == qrData.kelas || it.display == qrData.kelas }
                        ?: return@mapNotNull null
                    val subKelas = SmpSubClass.values().firstOrNull { it.name == qrData.subKelas || it.display == qrData.subKelas }
                        ?: return@mapNotNull null
                    StudentEntity(
                        uuid = UUID.randomUUID().toString(),
                        name = qrData.name,
                        phone = qrData.phone,
                        waliKelasPhone = qrData.waliKelasPhone,
                        kelas = kelas,
                        subKelas = subKelas,
                        violationCount = qrData.violationCount,
                        timestamps = qrData.timestamps
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Merge data dari QR dengan data lokal.
     *
     * Strategy:
     * 1. Primary key: UUID — if UUID matches, update existing student
     * 2. Fallback: Name — if UUID doesn't match but name matches, merge
     * 3. If no match, add as new student
     *
     * @param incoming Students from QR (may or may not have UUID)
     * @param local Current local students
     * @return Merged list
     */
    suspend fun mergeStudents(
        incoming: List<StudentEntity>,
        local: List<StudentEntity>
    ): List<StudentEntity> = withContext(Dispatchers.IO) {
        val localByUuid = local.associateBy { it.uuid }
        val localByName = local.associateBy { it.name }

        val merged = mutableListOf<StudentEntity>()
        val processedUuids = mutableSetOf<String>()

        incoming.forEach { incomingStudent ->
            // Try to find by UUID first
            val existingByUuid = localByUuid[incomingStudent.uuid]

            if (existingByUuid != null) {
                // Same UUID — update existing
                val mergedStudent = mergeStudentsInternal(existingByUuid, incomingStudent)
                merged.add(mergedStudent)
                processedUuids.add(incomingStudent.uuid)
            } else {
                // Try to find by name (fallback for legacy data)
                val existingByName = localByName[incomingStudent.name]
                if (existingByName != null && existingByName.uuid !in processedUuids) {
                    // Name matches — merge and keep existing UUID
                    val mergedStudent = mergeStudentsInternal(existingByName, incomingStudent)
                    merged.add(mergedStudent)
                    processedUuids.add(existingByName.uuid)
                } else {
                    // No match — add as new student with generated UUID
                    val newStudent = incomingStudent.copy(
                        uuid = if (incomingStudent.uuid.isNotEmpty()) {
                            incomingStudent.uuid
                        } else {
                            UUID.randomUUID().toString()
                        }
                    )
                    merged.add(newStudent)
                }
            }
        }

        // Add local students that weren't matched
        local.forEach { localStudent ->
            if (localStudent.uuid !in processedUuids) {
                merged.add(localStudent)
            }
        }

        merged
    }

    /**
     * Internal merge: combine two StudentEntity instances.
     * Takes the highest violationCount, merges timestamps, prefers non-empty phone.
     */
    private fun mergeStudentsInternal(existing: StudentEntity, incoming: StudentEntity): StudentEntity {
        val finalViolationCount = maxOf(incoming.violationCount, existing.violationCount)
        val mergedTimestamps = (existing.timestamps + incoming.timestamps).distinct().sorted()
        val finalPhone = if (incoming.phone.isNotBlank()) incoming.phone else existing.phone
        val finalWaliKelasPhone = incoming.waliKelasPhone ?: existing.waliKelasPhone

        return existing.copy(
            phone = finalPhone,
            waliKelasPhone = finalWaliKelasPhone,
            kelas = incoming.kelas,
            subKelas = incoming.subKelas,
            violationCount = finalViolationCount,
            timestamps = mergedTimestamps
        )
    }

    /**
     * Check if a string is a legacy QR code (Base64 JSON without GZIP).
     * Delegates to CompactSerializer.isLegacyFormat.
     */
    suspend fun isLegacyFormat(encoded: String): Boolean {
        return CompactSerializer.isLegacyFormat(encoded)
    }
}
