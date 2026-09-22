package com.flambeauriver.dailydriver.slm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [InferenceEngine] over MediaPipe's LLM Inference API, GPU backend (T029, ADR-0003).
 *
 * **Nothing constructs this yet, deliberately.** [NoInferenceEngine] is still what the command
 * pipeline uses; wiring T2 in is T036's job, after T037's gate says whether LoRA is viable at all.
 * An engine reachable only from a test cannot put the SLM on the launcher's fast path by accident,
 * and that is a root hard constraint rather than a preference.
 *
 * **This class has no unit-test surface, and that is structural rather than an omission.** Every
 * method here ends in a JNI call into a 26 MB native library that cannot load on a JVM. What *is*
 * testable was pulled out into [ModelLocation], which is where the decisions live; what remains is
 * the adapter. It is verified on the target device (SM-G781V) and nowhere else — CI's emulator
 * runs `-gpu swiftshader_indirect`, and Google's guide says the API "does not reliably support
 * device emulators", so a green CI run says nothing about this file beyond "it compiles and
 * installs".
 *
 * **The options are split across two objects because the API is** (verified against the 0.10.35
 * AAR, not the guide, which is several versions behind). `LlmInferenceOptions` carries the
 * model-level settings — path, `maxTokens`, `maxTopK`, `supportedLoraRanks`, `preferredBackend` —
 * and `LlmInferenceSessionOptions` carries the per-generation ones, including `temperature`,
 * `topK` and **`loraPath`**. Design doc §4.4 lists them as one flat set, which is no longer true
 * and matters for T037: applying a LoRA adapter is two steps in two places, and the supported
 * ranks are declared when the engine is built, before any adapter has been chosen.
 */
class MediaPipeEngine(
    private val context: Context,
    private val location: ModelLocation,
) : InferenceEngine {

    /**
     * Serialises load, infer, and the teardown inside them.
     *
     * `LlmInference` is not documented as thread-safe, and T2 will be called from whatever
     * coroutine the shell happens to be on. One lock is cheaper to reason about than discovering
     * the answer empirically on a phone.
     */
    private val mutex = Mutex()

    @Volatile
    private var inference: LlmInference? = null

    @Volatile
    override var state: InferenceEngine.State =
        if (location.isPresent()) InferenceEngine.State.Ready else InferenceEngine.State.Absent
        private set

    /**
     * Reads the model into memory, or records why it could not.
     *
     * **Presence is re-checked here rather than trusted from construction.** The model arrives
     * after first run (§4.5), so an engine built at process start is legitimately Absent and
     * legitimately Ready ten minutes later, with nothing having been rebuilt in between.
     *
     * **Catches [Throwable], not [Exception].** The failure this most needs to survive is
     * `UnsatisfiedLinkError` — an `Error` — which is what a missing or wrong-ABI native library
     * throws. Letting that propagate would crash the home screen over an optional feature, which
     * §4.2 forbids in the strongest terms the design doc uses. The blocking call inside cannot
     * raise `CancellationException`, so nothing cancellable is being swallowed.
     */
    override suspend fun load() {
        mutex.withLock {
            if (inference != null) return
            val path = location.path
            if (path == null || !location.isPresent()) {
                state = InferenceEngine.State.Absent
                return
            }
            state = InferenceEngine.State.Loading
            state = withContext(Dispatchers.IO) {
                try {
                    inference = LlmInference.createFromOptions(context, optionsFor(path))
                    InferenceEngine.State.Loaded
                } catch (t: Throwable) {
                    inference = null
                    InferenceEngine.State.Failed
                }
            }
        }
    }

    /**
     * One prompt in, raw model output or null out.
     *
     * **A fresh session per call, never a long-lived one.** A session accumulates everything added
     * to it, so reusing one would let the previous command's text condition the next command's
     * answer. Each line typed at the prompt is an independent extraction, and two identical inputs
     * resolving differently because of what was typed before them would corrupt both the T2 tier
     * and the T014 log that seeds §8's dataset.
     *
     * **A failure here returns null but leaves the engine Loaded**, which reads as a departure
     * from [InferenceEngine.State.Failed]'s "load or inference failed" and is a deliberate one.
     * One generation failing is the model declining one input; condemning the engine would disable
     * T2 for the rest of the process over a single awkward prompt. Null is already an ordinary
     * outcome routed to T3, so nothing is lost by staying loaded. Load failure still latches,
     * because that genuinely is the engine being unusable.
     *
     * Runs on [Dispatchers.Default] rather than IO: the weights are already resident, so this is
     * GPU and CPU work, not a disk read.
     */
    override suspend fun infer(prompt: String): String? {
        val engine = inference ?: return null
        return mutex.withLock {
            withContext(Dispatchers.Default) {
                var session: LlmInferenceSession? = null
                try {
                    session = LlmInferenceSession.createFromOptions(engine, sessionOptions())
                    session.addQueryChunk(prompt)
                    session.generateResponse()
                } catch (t: Throwable) {
                    null
                } finally {
                    session?.closeQuietly()
                }
            }
        }
    }

    /**
     * Releases the model and returns to Ready or Absent.
     *
     * Not suspending, because [InferenceEngine] says so, so it cannot take [mutex] — a caller that
     * closes an engine mid-inference gets whatever MediaPipe does about that. T036 owns the
     * lifecycle and will only close from the same place it loads.
     */
    override fun close() {
        val engine = inference ?: return
        inference = null
        state =
            if (location.isPresent()) InferenceEngine.State.Ready else InferenceEngine.State.Absent
        engine.closeQuietly()
    }

    /**
     * Model-level options.
     *
     * **`setPreferredBackend(GPU)` is not on its own enough, and the method name is the warning.**
     * The conversion step (T030) also bakes a backend into the `.task` artifact, and MediaPipe
     * documents LoRA as GPU-only. A model converted for CPU and an engine *preferring* GPU is a
     * plausible way to fail T037's gate for a reason that has nothing to do with the device.
     *
     * **No `setSupportedLoraRanks` yet.** The rank has to match a trained adapter and none exists;
     * guessing one here would look like configuration and behave like a bug. It belongs to T037.
     */
    private fun optionsFor(path: String): LlmInference.LlmInferenceOptions =
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .setPreferredBackend(LlmInference.Backend.GPU)
            .build()

    /**
     * Per-generation options. **T032 owns these numbers** — they are here because a session cannot
     * be built without them, not because this is where sampling gets decided.
     */
    private fun sessionOptions(): LlmInferenceSession.LlmInferenceSessionOptions =
        LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTemperature(TEMPERATURE)
            .setTopK(TOP_K)
            .setRandomSeed(RANDOM_SEED)
            .build()

    private fun AutoCloseable.closeQuietly() {
        try {
            close()
        } catch (t: Throwable) {
            // Releasing a native handle that is already gone must not take the launcher with it.
        }
    }

    companion object {
        /** §4.4: 512 is ample, because a command is one line and the reply is a small object. */
        private const val MAX_TOKENS = 512

        /** §4.4: this is extraction, not creative writing. */
        private const val TEMPERATURE = 0.1f

        /**
         * MediaPipe's own default. Set on both objects on purpose: a session `topK` above the
         * model's `maxTopK` is rejected, so pinning them together means changing one at T032
         * cannot silently break the other.
         */
        private const val TOP_K = 40

        /**
         * Fixed, so T038's latency numbers and T083's benchmark compare runs rather than samples.
         * A varying seed puts noise into the one measurement the GO/NO-GO decision rests on.
         */
        private const val RANDOM_SEED = 0
    }
}
