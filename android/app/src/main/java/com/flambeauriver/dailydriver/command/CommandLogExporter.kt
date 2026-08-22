package com.flambeauriver.dailydriver.command

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Writing the command log to Downloads, from inside the app (T166).
 *
 * **This exists because `run-as` does not work on the build being daily-driven.**
 * `scripts/export-command-log.sh` reads app-private storage through `run-as`, which requires a
 * debuggable build; the phone runs a signed release from T159, so the script refuses and the
 * training data T014 has been collecting is unreachable. T014 collects, T159 ships, and until this
 * landed the two did not meet.
 *
 * **⚠️ The wrong fix would have been to make release builds debuggable.** That exposes every
 * app-private file on the device you carry to anything with adb access, permanently, to make an
 * occasional maintenance task easier. Exporting deliberately, to a location the user can see, is
 * the trade the other way round: nothing leaves unless someone types `:export`.
 *
 * MediaStore rather than a `File` in `/sdcard/Download`: direct writes there have needed a
 * permission the app doesn't hold since API 29, and minSdk here is 33. Going through MediaStore
 * needs no permission at all for a file the app creates, and puts the result somewhere a file
 * manager, a USB cable and `adb pull` can all reach.
 */
class CommandLogExporter(private val context: Context) {

    /**
     * Writes [entries] as JSONL and returns where it landed.
     *
     * **The reported location is queried back from MediaStore, not the name that was asked for.**
     * MediaStore may rename what it stores — it reconciles the display name's extension against
     * the MIME type — and T166 is entirely a story about trusting a route that quietly did
     * something other than what it looked like. Telling the user where the file *actually* is
     * costs one query.
     */
    suspend fun export(entries: List<CommandLogEntry>, at: LocalDateTime): CommandLogExportResult =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = commandLogFileName(at)

            // IS_PENDING hides the row until the write finishes, so a file manager can never show
            // a half-written export as a finished one.
            val pending = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, JSONL_MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, pending)
            }.getOrNull() ?: return@withContext CommandLogExportResult.Failed(
                "could not create a file in Downloads",
            )

            runCatching {
                val bytes = commandLogJsonl(entries).toByteArray(Charsets.UTF_8)
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("could not open the file for writing")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }.fold(
                onSuccess = {
                    CommandLogExportResult.Written(
                        location = resolver.describe(uri) ?: name,
                        stats = commandLogStats(entries),
                    )
                },
                onFailure = { failure ->
                    // Clean up the pending row. It is invisible to file managers but real, and a
                    // half-written export that is later un-hidden by anything is worse than none.
                    runCatching { resolver.delete(uri, null, null) }
                    CommandLogExportResult.Failed(failure.message ?: "could not write the file")
                },
            )
        }
}

/**
 * **Chosen by testing on-device, not from the registry.** MediaStore reconciles the display name's
 * extension against the MIME type: `application/json` maps to `.json`, disagrees with `.jsonl`,
 * and gets you `dailydriver-command-log-2026-08-22.jsonl.json`. Nothing maps to `.jsonl`, so an
 * unmapped type is what leaves the name alone — the opposite of what picking the "more correct"
 * registered MIME would do.
 */
private const val JSONL_MIME = "application/x-ndjson"

/** Where MediaStore actually put it: `RELATIVE_PATH` already carries its trailing slash. */
private fun ContentResolver.describe(uri: Uri): String? = runCatching {
    query(
        uri,
        arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) "${cursor.getString(0)}${cursor.getString(1)}" else null
    }
}.getOrNull()

sealed interface CommandLogExportResult {

    /** [location] is a user-facing path like `Download/dailydriver-command-log-2026-08-22-131045.jsonl`. */
    data class Written(val location: String, val stats: CommandLogStats) : CommandLogExportResult

    /** [reason] is shown verbatim in the transcript — it has to read as a sentence, not a stack frame. */
    data class Failed(val reason: String) : CommandLogExportResult
}
