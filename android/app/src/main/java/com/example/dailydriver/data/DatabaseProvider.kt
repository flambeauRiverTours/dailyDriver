package com.example.dailydriver.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Builds [DailyDriverDatabase] once and hands the same instance to everything that needs it.
 *
 * A singleton because there is now more than one caller — aliases (T007) and notes (T018) share
 * a database, and letting each repository run its own `databaseBuilder` against the same file
 * would mean two connection pools over one SQLite file, which is exactly how you get lock
 * contention and a transaction that doesn't see its own sibling's writes.
 *
 * Holds `applicationContext`, never an Activity: the instance outlives every Activity, and Room's
 * own documentation warns against the alternative.
 */
object DatabaseProvider {

    @Volatile
    private var instance: DailyDriverDatabase? = null

    fun get(context: Context): DailyDriverDatabase =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    private fun build(appContext: Context): DailyDriverDatabase =
        Room.databaseBuilder(appContext, DailyDriverDatabase::class.java, DailyDriverDatabase.NAME)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Fires once, on the transition off T007's aliases-only database.
                        importLegacyAliases(appContext, db)
                    }
                },
            )
            .build()
}
