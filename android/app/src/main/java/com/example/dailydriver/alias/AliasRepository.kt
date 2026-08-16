package com.example.dailydriver.alias

import android.content.Context
import androidx.room.Room

/**
 * Owns the Room database and keeps [AliasIndex] in sync with it. Mirrors AppIndexRepository's
 * shape: Context-owning glue, not unit-tested directly — no separable pure logic here beyond
 * what AliasDefinition.kt and AliasIndex.kt already cover on their own.
 *
 * Takes applicationContext, not the raw Context: unlike AppIndexRepository/AppLauncher (short-
 * lived, one-shot operations), the Room database instance this builds is meant to be held for
 * the app's lifetime, and Room's own docs warn against tying that to an Activity context.
 */
class AliasRepository(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        AliasDatabase::class.java,
        "aliases.db",
    ).build()
    private val dao = db.aliasDao()
    private val index = AliasIndex()

    val aliases: Map<String, String> get() = index.asMap()

    suspend fun refresh() {
        index.replaceAll(dao.getAll())
    }

    /**
     * Evaluates [name]/[command] against the currently loaded aliases (see
     * [evaluateAliasDefinition] for why that check alone rules out cycles of any length) and,
     * unless rejected, persists it and refreshes the index.
     */
    suspend fun define(name: String, command: String): DefineAliasResult {
        val result = evaluateAliasDefinition(name, command, index.asMap())
        val accepted = result is DefineAliasResult.Created || result is DefineAliasResult.Overwritten
        if (accepted) {
            dao.upsert(Alias(name, command))
            refresh()
        }
        return result
    }
}
