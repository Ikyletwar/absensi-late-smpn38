package com.osis.smkn1malteng.absensilate.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [StudentEntity::class, AppConfigEntity::class],
    version = 8,  // 🔥 VERSION 8 — maxViolation default kini 3 (migration v7→v8)
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun configDao(): AppConfigDao

    companion object {
        private const val TAG = "AppDatabase"
        @Volatile private var INSTANCE: AppDatabase? = null

        // 🔥 MIGRATION v1 → v2: Add uuid column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v1→v2: add uuid column")
                db.execSQL("ALTER TABLE students ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE students SET uuid = 'migrated_' || id WHERE uuid = ''")
            }
        }

        // 🔥 MIGRATION v2 → v3: Rename parentPhone to waliKelasPhone
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v2→v3: rename parentPhone → waliKelasPhone")
                db.execSQL("ALTER TABLE students RENAME COLUMN parentPhone TO waliKelasPhone")
            }
        }

        // 🔥 MIGRATION v3 → v4: Rename jurusan to subKelas
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v3→v4: rename jurusan → subKelas")
                db.execSQL("ALTER TABLE students RENAME COLUMN jurusan TO subKelas")
            }
        }

        // 🔥 MIGRATION v4 → v5: Add indices for performance (720+ siswa)
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v4→v5: add indices on kelas & subKelas")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_students_kelas ON students (kelas)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_students_subKelas ON students (subKelas)")
            }
        }

        // 🔥 MIGRATION v5 → v6: Convert old SMK enum names to new SMP names
        //    Handles DB from SMKN 1 version (enum: TIKR/TKRO/TJKT/etc → VII/VIII/IX)
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v5→v6: convert old SMK enum names → SMP")

                // === Convert kelas column ===
                db.execSQL("""
                    UPDATE students SET kelas = CASE
                        WHEN kelas IN ('TIKR', 'TKRO') THEN 'VII'
                        WHEN kelas IN ('TJKT', 'MULTIMEDIA') THEN 'VIII'
                        WHEN kelas IN ('AKUNTANSI', 'BISNIS', 'PEMASARAN', 'TEKNOLOGI_LAB') THEN 'IX'
                        ELSE kelas
                    END
                    WHERE kelas NOT IN ('VII', 'VIII', 'IX')
                """)

                // === Convert subKelas column ===
                db.execSQL("""
                    UPDATE students SET subKelas = CASE
                        WHEN subKelas IN ('TJKT 1', 'TJKT1', 'TIKR 1', 'TIKR1') THEN 'SAINS'
                        WHEN subKelas IN ('TJKT 2', 'TJKT2', 'TKRO 1', 'TKRO1') THEN 'BILINGUAL'
                        WHEN subKelas IN ('TJKT 3', 'TJKT3', 'TIKR 2', 'TIKR2') THEN 'SENI'
                        WHEN subKelas IN ('MULTIMEDIA 1', 'AKUNTANSI 1') THEN 'SUB_1'
                        WHEN subKelas IN ('MULTIMEDIA 2', 'AKUNTANSI 2') THEN 'SUB_2'
                        WHEN subKelas IN ('BISNIS 1', 'PEMASARAN 1') THEN 'SUB_3'
                        ELSE subKelas
                    END
                    WHERE subKelas NOT IN ('SAINS', 'BILINGUAL', 'SENI', 'SUB_1', 'SUB_2', 'SUB_3', 'SUB_4', 'SUB_5')
                """)

                Log.d(TAG, "Migration v5→v6 complete")
            }
        }

        // 🔥 MIGRATION v6 → v7: Add maxViolation column to app_config (single source of truth
        //    untuk ambang pelanggaran panggilan orang tua). Default 2 = perilaku lama.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v6→v7: add maxViolation to app_config")
                db.execSQL("ALTER TABLE app_config ADD COLUMN maxViolation INTEGER NOT NULL DEFAULT 2")
                Log.d(TAG, "Migration v6→v7 complete")
            }
        }

        // 🔥 MIGRATION v7 → v8: Default max pelanggaran panggilan ortu berubah 2 → 3.
        //    UPDATE nilai yang masih 2 (default lama) menjadi 3. Nilai selain 2 tidak disentuh.
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                Log.d(TAG, "Migrating v7→v8: default maxViolation 2 → 3")
                db.execSQL("UPDATE app_config SET maxViolation = 3 WHERE maxViolation = 2")
                Log.d(TAG, "Migration v7→v8 complete")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    Log.d(TAG, "Building Room database 'absensi.db' v8")
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "absensi.db"
                    )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8
                    )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            Log.d(TAG, "Database CREATED (fresh install)")
                        }
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            Log.d(TAG, "Database OPENED")
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
                }
            }
        }
    }
}
