package com.flambeauriver.dailydriver.alias

import android.content.Context
import com.flambeauriver.dailydriver.data.DatabaseProvider

/**
 * Keeps [AliasIndex] in sync with the stored aliases. Mirrors AppIndexRepository's shape:
 * Context-owning glue, not unit-tested directly — no separable pure logic here beyond what
 * AliasDefinition.kt and AliasIndex.kt already cover on their own.
 *
 * No longer builds its own database. Since T018 aliases share one with notes and the outbox, so
 * the instance comes from [DatabaseProvider] — see there for why a second builder over the same
 * file would be a bug rather than a duplication.
 */
class AliasRepository(context: Context) {

    private val dao = DatabaseProvider.get(context).aliasDao()
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
