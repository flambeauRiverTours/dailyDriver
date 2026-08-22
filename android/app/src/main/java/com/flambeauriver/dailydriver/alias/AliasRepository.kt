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
    /**
     * Stores an alias, if [target] says it points at something real (T162).
     *
     * [target] is resolved by the caller rather than here, because validation needs the app index
     * and this repository owns aliases. Defaulting it to [AliasTarget.Command] keeps every existing
     * caller and test working: an unvalidated definition behaves exactly as it did before.
     *
     * **What is stored comes from the result, not from [command].** An app target is rewritten to
     * the app's full label so the alias cannot silently repoint later, and writing the raw input
     * here would throw that away while still reporting the resolved name back to the user — the
     * two would disagree from the moment it was created.
     */
    suspend fun define(
        name: String,
        command: String,
        target: AliasTarget = AliasTarget.Command,
    ): DefineAliasResult {
        val result = evaluateAliasDefinition(name, command, index.asMap(), target)

        val stored = when (result) {
            is DefineAliasResult.Created -> result.command
            is DefineAliasResult.Overwritten -> result.command
            else -> null
        }

        if (stored != null) {
            dao.upsert(Alias(name, stored))
            refresh()
        }
        return result
    }
}
