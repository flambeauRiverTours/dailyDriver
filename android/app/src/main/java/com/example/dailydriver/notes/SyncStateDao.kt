package com.example.dailydriver.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The §7.1 pull watermark. See [SyncState] for why this is a table and not a preference. */
@Dao
interface SyncStateDao {

    @Query("SELECT lastPulledRev FROM sync_state WHERE id = ${SyncState.SINGLETON_ID}")
    suspend fun lastPulledRev(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(state: SyncState)
}
