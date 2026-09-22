package com.flambeauriver.dailydriver.slm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * [ModelLocation] is the whole of "is there a model on this device", and it is the only part of
 * T029 with a unit-test surface — [MediaPipeEngine] loads a native library and cannot run off a
 * device at all.
 *
 * These use real files in a temporary directory rather than an abstraction over the filesystem.
 * The questions being asked are all filesystem questions ("did the push land", "is it a directory
 * by mistake"), and a fake that answered them would be asserting its own behaviour.
 */
class ModelLocationTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a null base directory is not present`() {
        // getExternalFilesDir returns null when external storage is not mounted. That is a real
        // state on a real phone, not a defensive check: it is what the launcher sees if it starts
        // while the volume is unavailable. Absent is the right answer and T3 handles it.
        val location = ModelLocation(baseDir = null)

        assertFalse(location.isPresent())
        assertNull(location.path)
    }

    @Test
    fun `a missing file is not present`() {
        // The ordinary case for the entire first session, and for every install until T087's
        // download flow exists.
        assertFalse(ModelLocation(baseDir = folder.root).isPresent())
    }

    @Test
    fun `a zero byte file is not present`() {
        // An interrupted `adb push` leaves the destination created and empty. Treating that as
        // Ready hands MediaPipe a file it will reject deep inside native code, which surfaces as
        // an opaque load failure rather than "the model is not here yet".
        File(folder.root, ModelLocation.DEFAULT_FILE_NAME).createNewFile()

        assertFalse(ModelLocation(baseDir = folder.root).isPresent())
    }

    @Test
    fun `a file with content is present`() {
        File(folder.root, ModelLocation.DEFAULT_FILE_NAME).writeText("not really a model")

        assertTrue(ModelLocation(baseDir = folder.root).isPresent())
    }

    @Test
    fun `a directory where the model should be is not present`() {
        // `adb push local.task /path/to/dir/` with a trailing slash, or a push to a path whose
        // parent does not exist, both produce a directory with the model's name. It exists, it is
        // non-empty, and File.length() on it is meaningless — so exists() alone is not the test.
        File(folder.root, ModelLocation.DEFAULT_FILE_NAME).mkdirs()

        assertFalse(ModelLocation(baseDir = folder.root).isPresent())
    }

    @Test
    fun `the path is the model file inside the base directory`() {
        val location = ModelLocation(baseDir = folder.root)

        assertEquals(File(folder.root, ModelLocation.DEFAULT_FILE_NAME).absolutePath, location.path)
    }

    @Test
    fun `the file name is configurable so two models can sit side by side`() {
        // T039 decides between Gemma-2 2B and Gemma-3 1B, and T038 measures both. Having them
        // coexist on the device is what makes that a measurement rather than two install cycles.
        val location = ModelLocation(baseDir = folder.root, fileName = "gemma3-1b-it-q4.task")

        assertTrue(location.path!!.endsWith("gemma3-1b-it-q4.task"))
    }
}
