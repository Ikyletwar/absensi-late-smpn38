package com.osis.smkn1malteng.absensilate.data.local

import android.util.Log
import androidx.room.TypeConverter
import com.osis.smkn1malteng.absensilate.data.model.SmpClass
import com.osis.smkn1malteng.absensilate.data.model.SmpSubClass

class Converters {

    // ============================================
    // TIMESTAMP LIST
    // ============================================
    @TypeConverter
    fun fromList(value: List<Long>) = value.joinToString(",")

    @TypeConverter
    fun toList(value: String) = if (value.isEmpty()) emptyList() else value.split(",").mapNotNull {
        try { it.trim().toLong() } catch (_: Exception) { null }
    }

    // ============================================
    // SMP CLASS — tolerates old SMK enum names
    // ============================================
    @TypeConverter
    fun fromSmpClass(value: SmpClass): String = value.name

    @TypeConverter
    fun toSmpClass(value: String): SmpClass {
        // 1. Exact match on enum name (new SMP: "VII", "VIII", "IX")
        SmpClass.values().firstOrNull { it.name == value }?.let { return it }

        // 2. Match on display value ("7", "8", "9")
        SmpClass.values().firstOrNull { it.display == value }?.let { return it }

        // 3. Old SMK enum names → new SMP mapping
        val mapped = when (value.uppercase()) {
            "TIKR", "TKRO" -> SmpClass.VII
            "TJKT", "MULTIMEDIA" -> SmpClass.VIII
            "AKUNTANSI", "BISNIS", "PEMASARAN", "TEKNOLOGI_LAB" -> SmpClass.IX
            else -> SmpClass.VII  // ultimate fallback
        }
        Log.w("Converters", "SmpClass: old value '$value' → ${mapped.name}")
        return mapped
    }

    // ============================================
    // SMP SUB CLASS — tolerates old SMK enum names
    // ============================================
    @TypeConverter
    fun fromSmpSubClass(value: SmpSubClass): String = value.name

    @TypeConverter
    fun toSmpSubClass(value: String): SmpSubClass {
        // 1. Exact match on enum name (new SMP: "SAINS", "BILINGUAL", "SUB_1", ...)
        SmpSubClass.values().firstOrNull { it.name == value }?.let { return it }

        // 2. Match on display value ("Sains", "Bil", "1", "2", ...)
        SmpSubClass.values().firstOrNull { it.display.equals(value, ignoreCase = true) }?.let { return it }

        // 3. Old SMK values that look like old sub-kelas names
        val mapped = when (value.uppercase()) {
            "TJKT 1", "TJKT1", "TIKR 1", "TIKR1" -> SmpSubClass.SAINS
            "TJKT 2", "TJKT2", "TKRO 1", "TKRO1" -> SmpSubClass.BILINGUAL
            "TJKT 3", "TJKT3", "TIKR 2", "TIKR2" -> SmpSubClass.SENI
            "MULTIMEDIA 1", "AKUNTANSI 1" -> SmpSubClass.SUB_1
            "MULTIMEDIA 2", "AKUNTANSI 2" -> SmpSubClass.SUB_2
            "BISNIS 1", "PEMASARAN 1" -> SmpSubClass.SUB_3
            else -> SmpSubClass.SUB_1  // ultimate fallback
        }
        Log.w("Converters", "SmpSubClass: old value '$value' → ${mapped.name}")
        return mapped
    }
}
