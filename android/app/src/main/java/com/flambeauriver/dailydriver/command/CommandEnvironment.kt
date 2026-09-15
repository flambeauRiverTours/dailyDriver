package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.alias.DefineAliasResult
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteQuery
import java.time.LocalDate

/**
 * Everything a command needs that a ViewModel cannot reach on its own (T178).
 *
 * **This interface is the whole fix.** Before it, `ShellViewModel.onSubmit` was a twelve-branch
 * `when` in which eleven branches were a bare `return`, each commented to say that `ShellScreen`
 * intercepted the command first. That was forced rather than careless: launching an app needs a
 * `Context`, adding a task needs a suspending Room write, and opening a note needs the editor —
 * none of which a ViewModel holds. So every command added since was answered with another
 * callback parameter, and dispatch ended up living in the composable.
 *
 * Naming the capabilities makes the ViewModel able to own dispatch again, and `MainActivity` — which
 * already holds `IntentLauncher`, the repositories and the editor — is the only implementation.
 *
 * **The other half of the point is testability.** A fake implementation makes every command's
 * behaviour reachable from a unit test, which none of it was: the dispatch logic simply had no
 * seam a test could get at.
 */
interface CommandEnvironment {

    /** The installed-app index, for T1 matching and `:apps`. */
    val apps: List<AppEntry>

    /** Currently defined aliases, for expansion and for `:alias`. */
    val aliases: Map<String, String>

    /** True once notification access has been granted; `:notif` behaves differently without it. */
    val hasNotificationAccess: Boolean

    fun launchApp(packageName: String): Boolean

    fun openSettings(): Boolean

    fun openUrl(url: String): Boolean

    /** Deep-links to Settings → Notification access, for `:notif` before the permission exists. */
    fun openNotificationAccess(): Boolean

    /** Opens the editor on a new note, `title` being `:note`'s argument (blank for an untitled one). */
    fun openEditor(title: String)

    /** Opens the editor on a note that already exists — `:notes` narrowed to one. */
    fun openNote(note: Note)

    /**
     * A one-shot read of the notes matching a query.
     *
     * Dispatch does its own lookup rather than borrowing the list `ShellScreen` collects for the
     * browse view. That list is *rendering* state arriving from a flow; making Enter depend on it
     * would put a piece of dispatch back in the composable, which is what this task exists to undo.
     */
    suspend fun notesMatching(query: NoteQuery): List<Note>

    suspend fun addTask(text: String, due: LocalDate): Unit

    suspend fun defineAlias(name: String, command: String): DefineAliasResult

    suspend fun exportCommandLog(): CommandLogExportResult

    /**
     * Records what the deterministic tiers did with a submitted line (T014), and counts the use
     * for ranking (T183).
     *
     * **Fire-and-forget by contract.** Both writes are a by-product of running a command, and a
     * launcher that made you wait on a database write before opening an app would have its
     * priorities backwards.
     *
     * **One call, two writes, two different gates** — see the implementation. The log keeps T3
     * misses because they are §8's `unknown` examples; the usage index must not, because there is
     * nothing to rank.
     */
    fun recordResolution(input: String, resolution: CommandResolution)

    /**
     * Use counts by target, for ranking candidates (T183).
     *
     * Read once per pass and handed to `rankCandidates` as a plain map, which is what keeps that
     * function pure and testable without a database.
     */
    suspend fun usageCounts(): Map<String, Int>
}
