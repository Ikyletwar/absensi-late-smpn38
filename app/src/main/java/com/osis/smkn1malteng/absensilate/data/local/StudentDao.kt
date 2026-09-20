package com.osis.smkn1malteng.absensilate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY id DESC")
    fun getAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE violationCount >= :threshold ORDER BY violationCount DESC")
    fun getStudentsWithViolations(threshold: Int): Flow<List<StudentEntity>>

    @Query("SELECT COUNT(*) FROM students")
    suspend fun getCount(): Int

    @Query("SELECT * FROM students WHERE name = :name LIMIT 1")
    suspend fun getStudentByName(name: String): StudentEntity?

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentEntity>)

    @Update
    suspend fun update(student: StudentEntity)

    @Query("DELETE FROM students")
    suspend fun deleteAll()

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE students SET violationCount = 0")
    suspend fun resetAllCounters()

    // ============================================
    // 🔥 CRUD PELANGGARAN PER SISWA
    // ============================================

    // Kurangi violationCount sebanyak 1 (min 0)
    @Query("UPDATE students SET violationCount = violationCount - 1 WHERE id = :id AND violationCount > 0")
    suspend fun decrementViolationCount(id: Long)

    // Reset pelanggaran untuk satu siswa (violationCount = 0, timestamps = empty)
    @Query("UPDATE students SET violationCount = 0, timestamps = '' WHERE id = :id")
    suspend fun resetStudentViolations(id: Long)

    // Tambah +1 pelanggaran sekaligus untuk banyak siswa (batch select mode)
    // timestamps = CSV string ("1,2,3") sehingga aman digabung dengan timestamp baru.
    @Query("UPDATE students SET violationCount = violationCount + 1, " +
        "timestamps = CASE WHEN timestamps = '' THEN :timestamp " +
        "ELSE timestamps || ',' || :timestamp END WHERE id IN (:ids)")
    suspend fun addViolationsBatch(ids: List<Long>, timestamp: Long)
}
