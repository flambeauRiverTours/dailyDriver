package com.example.dailydriver.alias

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A stored alias — [name] is the trigger typed at the prompt, [command] is the text substituted
 * for it. Device-local, not synced (design doc §4.7).
 */
@Entity(tableName = "aliases")
data class Alias(
    @PrimaryKey val name: String,
    val command: String,
)
