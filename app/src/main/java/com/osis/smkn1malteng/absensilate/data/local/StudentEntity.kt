package com.osis.smkn1malteng.absensilate.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass
import java.util.UUID

@Entity(
    tableName = "students",
    indices = [
        Index(value = ["kelas"]),
        Index(value = ["subKelas"])
    ]
)
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),  // 🔥 NEW: Unique identifier for sync
    val name: String,
    val phone: String,
    val waliKelasPhone: String? = null,
    val kelas: SmpClass,
    val subKelas: SmpSubClass,
    var violationCount: Int = 0,
    val timestamps: List<Long> = emptyList()
)
