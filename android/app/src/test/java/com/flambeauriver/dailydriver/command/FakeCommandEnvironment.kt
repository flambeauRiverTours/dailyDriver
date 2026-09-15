package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.alias.DefineAliasResult
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.notes.Note
import com.flambeauriver.dailydriver.notes.NoteQuery
import java.time.LocalDate

/**
 * A recording [CommandEnvironment] for tests (T178).
 *
 * **This is the point of the interface existing.** Before it, every command's behaviour lived in
 * `ShellScreen`'s Enter handler and had no seam a unit test could reach, so none of it was tested
 * at all. Each field below is what one command is supposed to have done.
 */
class FakeCommandEnvironment(
    override var apps: List<AppEntry> = emptyList(),
    override var aliases: Map<String, String> = emptyMap(),
    override var hasNotificationAccess: Boolean = true,
    /** Notes returned by [notesMatching], regardless of the query. */
    var notes: List<Note> = emptyList(),
    /** What [openUrl] reports — false stands in for a device with no browser. */
    var browserAvailable: Boolean = true,
    var exportResult: CommandLogExportResult =
        CommandLogExportResult.Written("Download/log.jsonl", CommandLogStats(1, 1, 0)),
    var aliasResult: DefineAliasResult = DefineAliasResult.Created("a", ":apps"),
) : CommandEnvironment {

    val launchedApps = mutableListOf<String>()
    val openedUrls = mutableListOf<String>()
    val openedEditors = mutableListOf<String>()
    val openedNotes = mutableListOf<Note>()
    val addedTasks = mutableListOf<Pair<String, LocalDate>>()
    val definedAliases = mutableListOf<Pair<String, String>>()
    val resolutions = mutableListOf<Pair<String, CommandResolution>>()
    /** Use counts, incremented by [recordResolution] exactly as the real one does. */
    val usage = mutableMapOf<String, Int>()
    var settingsOpened = 0
    var notificationAccessOpened = 0
    var exportsRequested = 0

    override fun launchApp(packageName: String): Boolean {
        launchedApps += packageName
        return true
    }

    override fun openSettings(): Boolean {
        settingsOpened++
        return true
    }

    override fun openUrl(url: String): Boolean {
        openedUrls += url
        return browserAvailable
    }

    override fun openNotificationAccess(): Boolean {
        notificationAccessOpened++
        return true
    }

    override fun openEditor(title: String) {
        openedEditors += title
    }

    override fun openNote(note: Note) {
        openedNotes += note
    }

    override suspend fun notesMatching(query: NoteQuery): List<Note> = notes

    override suspend fun addTask(text: String, due: LocalDate) {
        addedTasks += text to due
    }

    override suspend fun defineAlias(name: String, command: String): DefineAliasResult {
        definedAliases += name to command
        return aliasResult
    }

    override suspend fun exportCommandLog(): CommandLogExportResult {
        exportsRequested++
        return exportResult
    }

    override fun recordResolution(input: String, resolution: CommandResolution) {
        resolutions += input to resolution
        // Mirrors the real implementation's gate: a T3 miss is logged but never counted, because
        // UNRESOLVED is the empty string and there is nothing to rank.
        if (resolution.target.isNotEmpty()) {
            usage[resolution.target] = (usage[resolution.target] ?: 0) + 1
        }
    }

    override suspend fun usageCounts(): Map<String, Int> = usage
}
