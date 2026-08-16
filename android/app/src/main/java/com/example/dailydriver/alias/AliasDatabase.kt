package com.example.dailydriver.alias

import androidx.room.Database
import androidx.room.RoomDatabase

// exportSchema = false: no migrations exist yet (version 1) and there's no CI schema-history check
// to feed — set up room.schemaLocation via the Room Gradle plugin if that changes later.
@Database(entities = [Alias::class], version = 1, exportSchema = false)
abstract class AliasDatabase : RoomDatabase() {
    abstract fun aliasDao(): AliasDao
}
