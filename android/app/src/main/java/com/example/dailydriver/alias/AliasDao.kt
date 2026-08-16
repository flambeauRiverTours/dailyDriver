package com.example.dailydriver.alias

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AliasDao {
    @Query("SELECT * FROM aliases")
    suspend fun getAll(): List<Alias>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: Alias)
}
