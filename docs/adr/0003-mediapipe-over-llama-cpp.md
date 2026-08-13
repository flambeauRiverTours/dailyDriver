# 0003. MediaPipe LLM Inference API over llama.cpp for on-device inference

Status: Accepted

## Context

One tier of the command pipeline (T2) needs a small language model running entirely on the phone — no calling out to the cloud, full stop, that's a stated non-goal (design doc §1). On top of that, the model needs to load a fine-tuned LoRA adapter and run on the device's GPU fast enough to hit a 1.5-second p50 latency target (§2). Two realistic runtimes exist for this on Android: Google's MediaPipe LLM Inference API, and llama.cpp via a JNI wrapper.

## Decision

**MediaPipe LLM Inference API**, GPU backend, with a `LlmInference`/`LlmInferenceOptions`-based integration — not llama.cpp.

## Consequences

- The main appeal is that this is the well-documented path, not a DIY one. Google publishes Android integration guidance for MediaPipe directly, including a defined LoRA workflow — PEFT training against specific attention layers, conversion via `converter.ConversionConfig()`, GPU-backend-only application — and that concrete, documented path is what the whole model-selection and fine-tuning plan in §4.4 is built around.
- The real cost is that MediaPipe can't force the model's output to be valid JSON. llama.cpp can, through GBNF grammars, and MediaPipe has no equivalent. That gap is exactly why §4.4 builds a five-layer defense instead of trusting the runtime: a tight few-shot prompt, low temperature, extracting the first balanced `{...}` span, a repair pass for common formatting mistakes, and schema validation that drops to the T3 fallback on failure. All five layers exist because this decision gave up the one guarantee that would have made most of them unnecessary.
- There's also a real risk that this bet doesn't age well. Google has put MediaPipe's LLM Inference API into maintenance-only mode and is steering new work toward LiteRT-LM instead. The plan isn't to avoid that risk, it's to design around it — every call to the model goes through an `InferenceEngine` interface, so a `LiteRtLmEngine` implementation can be swapped in later (already tracked as v2 work, T091/T092) without touching the rest of the command pipeline.
- One more constraint worth knowing: LoRA under MediaPipe only works on the GPU backend, and it's documented as experimental even there — it doesn't run on CPU at all. That directly limits which model can be used (Gemma-2 2B vs. Gemma-3 1B, decided separately in T039), and it's the whole reason T037 exists as a timeboxed go/no-go spike before any fine-tuning work begins.

## Alternatives considered

- **llama.cpp via an Android JNI wrapper.** This would have kept grammar-constrained decoding and made the five-layer JSON defense above mostly unnecessary. It lost anyway: building the fine-tuning and deployment pipeline against Google's own supported Android guide was judged lower-risk than assembling an equivalent LoRA-on-Android setup around a community wrapper, even at the cost of giving up guaranteed-valid output.
- **Cloud-hosted inference, from any provider.** This was never really in play — the project's non-goals say plainly that if it can't run on the phone, it's out of scope. That's not a close technical call, it's a stated portfolio requirement: the point is to demonstrate a complete *on-device* GenAI pipeline.
