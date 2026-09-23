package com.flambeauriver.dailydriver.slm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * What T029 can actually prove without a model (T030/T031 have not happened yet).
 *
 * **The point of these is that the failure changes shape.** Before this task the app had no
 * MediaPipe at all; the useful question now is not "does inference work" — it cannot, there are no
 * weights — but "did the 26 MB native library ship, land in the APK for this device's ABI, and
 * load". Those are three separate ways to be broken, all of which look identical from a green
 * build, and all of which would surface at T031 as a mysterious failure to load a model that was
 * in fact fine.
 *
 * These must stay runnable on the CI emulator, which is why debug keeps `x86_64` in its
 * `abiFilters`. The emulator cannot run *inference* (`-gpu swiftshader_indirect`, and Google's own
 * guide says the API "does not reliably support device emulators"), but it can answer every
 * question asked here.
 */
@RunWith(AndroidJUnit4::class)
class MediaPipeEngineTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theNativeInferenceLibraryLoads() {
        // The whole of T029's risk in one line. An `abiFilters` entry missing for this device, a
        // packaging rule that stripped lib/, or a dependency that resolved but shipped no native
        // code all fail here with UnsatisfiedLinkError, immediately and unambiguously — rather
        // than at T031, disguised as a bad model file.
        System.loadLibrary("llm_inference_engine_jni")
    }

    @Test
    fun aMissingModelIsAbsentRatherThanAFailure() {
        val engine = MediaPipeEngine(context, ModelLocation(baseDir = folder.root))

        runBlocking { engine.load() }

        // Absent, not Failed: there is nothing wrong here. This is the state every install is in
        // until T087's download flow runs, and it has to read as "no model yet" so the launcher
        // can say something true about it.
        assertEquals(InferenceEngine.State.Absent, engine.state)
        assertNull(runBlocking { engine.infer("open gmail") })
    }

    @Test
    fun aFileThatIsNotAModelFailsWithoutCrashing() {
        // Deliberately garbage, and deliberately non-empty so ModelLocation passes it through to
        // MediaPipe. This is the path an interrupted or corrupt download takes, and §4.2 says the
        // launcher may not die for the SLM — so the contract being checked is that a native-side
        // rejection arrives as a state, not as an exception escaping into the home screen.
        File(folder.root, ModelLocation.DEFAULT_FILE_NAME).writeText("this is not a .task file")
        val engine = MediaPipeEngine(context, ModelLocation(baseDir = folder.root))

        runBlocking { engine.load() }

        assertEquals(InferenceEngine.State.Failed, engine.state)
        assertNull(runBlocking { engine.infer("open gmail") })
    }

    @Test
    fun closeIsSafeOnAnEngineThatNeverLoaded() {
        val engine = MediaPipeEngine(context, ModelLocation(baseDir = folder.root))

        engine.close()
        engine.close()

        assertEquals(InferenceEngine.State.Absent, engine.state)
    }

    @Test
    fun theRealModelDirectoryIsReachable() {
        // getExternalFilesDir is what T031 will `adb push` into, and it returns null when the
        // external volume is not mounted. Failing here means the path chosen for the model does
        // not exist on this device, which is worth knowing before spending a 2.7 GB push on it.
        val location = ModelLocation(baseDir = context.getExternalFilesDir(null))

        // **Derived from the constant, never re-typed.** This test hardcoded "model.task" and
        // went red the moment T031 changed the default to `model.bin` — a duplicated constant in
        // the exact place that was meant to be the single source of truth for it.
        assertEquals(
            File(context.getExternalFilesDir(null), ModelLocation.DEFAULT_FILE_NAME).absolutePath,
            location.path,
        )
    }
}
