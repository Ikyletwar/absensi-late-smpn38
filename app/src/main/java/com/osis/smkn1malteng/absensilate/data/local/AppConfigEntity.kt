package com.osis.smkn1malteng.absensilate.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey(autoGenerate = false) val id: Int = 1,
    val lastRecordedWeek: Int,
    val lastRecordedYear: Int,
    // 🔥 Max pelanggaran sebelum panggilan orang tua (single source of truth). Default 3. Bebas: 1, 2, 3, dst.
    val maxViolation: Int = 3
)
