package com.flambeauriver.dailydriver.slm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **A spike, not a suite member.** Answers one question for T039: does MediaPipe 0.10.35 still
 * load the pre-`.task` `.bin` artifacts that Kaggle publishes for Gemma 2B?
 *
 * It matters because `gemma-2b-it-gpu-int4.bin` is 1.35 GB, which is the only LoRA-capable model
 * that fits §2's "< 1.5 GB resident" target — Gemma-2 2B publishes q8 only, at 2.63 GB. If this
 * file loads, T039 keeps both fine-tuning and the memory budget. If it does not, that option is
 * gone and the choice is between giving up LoRA and giving up the budget.
 *
 * **Every test here skips unless a `modelPath` instrumentation argument is supplied**, so this is
 * inert in CI and on any run that has not deliberately pushed a model. Run it with:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.flambeauriver.dailydriver.slm.ModelFormatSpikeTest \
 *   -Pandroid.testInstrumentationRunnerArguments.modelPath=/data/data/com.flambeauriver.dailydriver/files/spike.bin
 * ```
 *
 * **Both backends are tried, deliberately.** A failure on GPU alone is a backend problem and says
 * nothing about the format; the same failure on CPU as well is the format. Separating those is the
 * whole reason this file exists rather than a single load attempt whose error would be ambiguous.
 *
 * **Delete this once T039 is recorded.** It has no assertions worth keeping — it exists to make
 * the runtime print what it thinks of a file.
 */
@RunWith(AndroidJUnit4::class)
class ModelFormatSpikeTest {

    private val modelPath: String?
        get() = InstrumentationRegistry.getArguments().getString("modelPath")

    @Test
    fun loadsOnGpuBackend() = attemptLoad(LlmInference.Backend.GPU)

    @Test
    fun loadsOnCpuBackend() = attemptLoad(LlmInference.Backend.CPU)

    private fun attemptLoad(backend: LlmInference.Backend) {
        val path = modelPath
        assumeTrue("no modelPath argument supplied; spike skipped", path != null)
        val file = File(path!!)
        assumeTrue("model not present at $path", file.isFile && file.length() > 0)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(512)
            .setMaxTopK(40)
            .setPreferredBackend(backend)
            .build()

        // The result is reported by throwing, because a passing test tells us nothing useful and
        // a failing one carries the runtime's own words about the file — which is the finding.
        val engine = try {
            LlmInference.createFromOptions(context, options)
        } catch (t: Throwable) {
            throw AssertionError(
                "[$backend] ${file.length()} bytes at $path REJECTED by " +
                    "${t.javaClass.name}: ${t.message}",
                t,
            )
        }

        engine.use {
            throw AssertionError(
                "[$backend] ${file.length()} bytes at $path LOADED. " +
                    "This is the result we wanted; the throw is how the spike reports it.",
            )
        }
    }
}
