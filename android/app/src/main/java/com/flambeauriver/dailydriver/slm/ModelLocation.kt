package com.flambeauriver.dailydriver.slm

import java.io.File

/**
 * Where the `.task` model file is, and whether it is actually there (T029).
 *
 * **Takes the directory rather than a Context**, so this is plain JVM code with a unit-test
 * surface while [MediaPipeEngine] — which loads a 26 MB native library — has none. The caller
 * supplies `context.getExternalFilesDir(null)`.
 *
 * **`getExternalFilesDir`, not `filesDir`, and that is a deliberate departure from §4.5's
 * "app-private storage".** Both are app-scoped and both are cleared on uninstall, but only the
 * external one can be written by `adb push` without `run-as` — and `run-as` does not work on this
 * project's daily driver, because the phone runs a signed release build (see `android/CLAUDE.md`,
 * which records the same wall being hit by `scripts/export-command-log.sh`). Using `filesDir`
 * would mean the development path (T031) and the shipping path (T087) could never be the same
 * path, and the one we could actually exercise on the target device would be the one we don't
 * ship. The cost is that the file is readable by anything holding `READ_EXTERNAL_STORAGE`, which
 * for a redistributable Gemma artifact is not a secret worth defending.
 *
 * Push a model with:
 * ```
 * adb push model.task /sdcard/Android/data/com.flambeauriver.dailydriver/files/model.task
 * ```
 *
 * @param baseDir `getExternalFilesDir(null)`, which is **nullable** — it returns null when the
 *   external volume is not mounted. That is an ordinary device state, so it resolves to "no
 *   model" rather than to an exception.
 * @param fileName which model, so T038 can measure two without two install cycles.
 */
class ModelLocation(
    private val baseDir: File?,
    private val fileName: String = DEFAULT_FILE_NAME,
) {

    /** The model file, or null when there is nowhere to look. */
    private val file: File? = baseDir?.let { dir -> File(dir, fileName) }

    /** Absolute path for [MediaPipeEngine] and for anything reporting where to push a model. */
    val path: String? = file?.absolutePath

    /**
     * True when a usable-looking model is on disk.
     *
     * **Three conditions, not one.** `exists()` alone is satisfied by an interrupted `adb push`,
     * which leaves a zero-byte file, and by a push with a trailing slash, which leaves a
     * *directory* with the model's name. Both would be handed to MediaPipe and fail inside native
     * code as an opaque load error, when the true answer is the far more actionable "the model is
     * not here yet".
     *
     * **This is not an integrity check and must not grow into one.** It cannot tell a complete
     * model from half of one, and no size threshold would — the honest answer is the SHA-256
     * verification §4.5 specifies, which belongs to T087's download flow where the expected hash
     * is known. Asserting a plausible minimum size here would look like validation while catching
     * nothing that matters.
     */
    fun isPresent(): Boolean = file?.let { f -> f.isFile && f.length() > 0L } == true

    companion object {
        /**
         * Deliberately generic. T039 has not decided between Gemma-2 2B and Gemma-3 1B, and a
         * default naming one of them would read as a decision that has not been made.
         */
        const val DEFAULT_FILE_NAME = "model.task"
    }
}
