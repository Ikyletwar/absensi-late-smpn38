package com.osis.smkn1malteng.absensilate.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM app_config WHERE id = 1")
    suspend fun getConfig(): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: AppConfigEntity)
}
