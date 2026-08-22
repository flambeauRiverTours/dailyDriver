package com.flambeauriver.dailydriver.slm

/**
 * The T2 tier's model, behind an interface (T028).
 *
 * **The interface exists before the implementation, and that is the point.** MediaPipe's LLM
 * Inference API is in maintenance-only mode upstream, with Google directing new work to LiteRT-LM
 * (§4.4). Committing the command pipeline to a MediaPipe type would make that migration a rewrite
 * of everything T2 touches; behind this, it is one new class. Benchmarking both backends is itself
 * a portfolio artifact (T091/T092), which only stays cheap if nothing above this line knows which
 * one is running.
 *
 * **Everything here is allowed to fail, and the launcher is not** (§4.2). There is no engine on a
 * fresh install: weights are downloaded at first run (§4.5), so [state] is [State.Absent] for the
 * entire first session and possibly longer. Callers must treat inference as optional rather than
 * as a dependency — T3 answers whatever this cannot.
 */
interface InferenceEngine {

    /** What the engine can do right now. Callers check this rather than catching failures. */
    val state: State

    /**
     * Loads the model. Safe to call when already loaded.
     *
     * Suspending because this reads hundreds of megabytes from disk and can take seconds. It must
     * never be called on the path that opens an app.
     */
    suspend fun load()

    /**
     * Runs one prompt and returns the raw model output.
     *
     * **Returns the raw string, deliberately.** Extracting JSON from it, repairing it and
     * validating it are separate layers (T033–T035) precisely because MediaPipe has no
     * grammar-constrained decoding and the output cannot be trusted to be JSON at all. An engine
     * that returned a parsed object would have to decide what to do with malformed output, which
     * is a policy question this interface has no business answering.
     *
     * Returns null when the engine cannot answer — not loaded, or inference failed. Null is an
     * ordinary outcome routed to T3, not an error condition.
     */
    suspend fun infer(prompt: String): String?

    /** Releases the model. Idempotent. */
    fun close()

    /** Lifecycle as the caller sees it. */
    enum class State {
        /** No weights on device. The first-run download (§4.5) has not completed. */
        Absent,

        /** Weights present, not yet loaded into memory. */
        Ready,

        /** Load in progress. Inference will decline rather than block. */
        Loading,

        /** Loaded and usable. */
        Loaded,

        /** Load or inference failed. The launcher carries on at T0/T1/T3. */
        Failed,
    }
}

/**
 * The engine used when there is no model: everything is [InferenceEngine.State.Absent] and every
 * inference declines.
 *
 * Not a test double. **This is what ships until T036 wires a real engine in**, and it is what runs
 * on any device where the download has not happened, the hardware cannot support the GPU backend,
 * or the LoRA gate (T037) came back negative. Having a real implementation of "no model" means the
 * absence is exercised in production rather than being a null check nobody tests.
 */
object NoInferenceEngine : InferenceEngine {
    override val state = InferenceEngine.State.Absent
    override suspend fun load() = Unit
    override suspend fun infer(prompt: String): String? = null
    override fun close() = Unit
}
