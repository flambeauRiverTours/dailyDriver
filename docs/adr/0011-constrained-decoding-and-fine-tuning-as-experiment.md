# 0011. Constrained decoding, and fine-tuning as an experiment rather than a dependency

Status: Accepted

## Context

[ADR-0003](0003-mediapipe-over-llama-cpp.md) chose MediaPipe over llama.cpp and accepted exactly one significant cost: MediaPipe could not force the model's output to be valid JSON, where llama.cpp could through GBNF grammars. Everything downstream was shaped by that. Design doc §4.4 built a five-layer defence — a tight few-shot prompt, low temperature, extracting the first balanced `{...}` span, a repair pass, and schema validation that drops to T3 — and closed with the sentence that has been steering the whole ML plan since: *"Making step 5 fire rarely is the primary justification for fine-tuning at all."*

That sentence made fine-tuning **load-bearing**. If the only thing standing between the launcher and malformed output is a model that has learned to emit the right shape, then the fine-tune is not an enhancement, it is the mechanism. T039's model choice was then contorted around preserving LoRA: Gemma-2 2B is the only Gemma with documented LoRA support under MediaPipe, and it publishes only at q8, 2.63 GB, against §2's own "< 1.5 GB resident" target. LoRA requires the GPU backend, self-conversion is CPU-only, and T037's gate — does LoRA actually work on an Adreno 650, with MediaPipe documenting it as experimental — could sink the section.

Two things then turned out to be false.

**MediaPipe does have constrained decoding.** T029 found `setConstraintHandle(long)` on `LlmInferenceSessionOptions` and recorded it as an open question. Following it up: `libllm_inference_engine_jni.so` carries a complete implementation (`litert::lm::ConstrainedDecoder`, `ExecutorDecodeParams::SetConstraintDecoder`, an `odml::infra::llm_utils::ConstraintAdapter` with `Start`/`ComputeNext`/`ComputeBitmap`/`IsEnded`), and the builder that produces a handle ships in a companion artifact, **`com.google.ai.edge.localagents:localagents-fc`**, which declares its own dependency on `tasks-genai`. The chain is public:

```
LlmInference.getSentencePieceProcessorHandle(): long
  -> FstConstraintProvider(long)
  -> .createConstraint(List<Tool>, ConstraintOptions): Constraint
  -> .getConstraintHandle(): long
  -> LlmInferenceSessionOptions.builder().setConstraintHandle(...)
```

`Schema` carries an `enum` list and `ConstraintOptions` carries `ToolCallOnly`, so §4.3's closed-world label space is expressible as a hard constraint. A model physically cannot emit a nineteenth intent.

**And the portfolio goal was misread.** Root `CLAUDE.md` lists the fine-tune-to-benchmark pipeline as a first-class goal, which was taken to mean the shipped model must be fine-tuned. Restated by the author (2026-09-22): the value is demonstrating that fine-tuning was *done*, not that the daily driver depends on it.

## Decision

**Adopt constrained decoding as the reliability mechanism for T2, and demote fine-tuning from a dependency to a measured experiment.**

Concretely:

- `localagents-fc` is added and the intent/slot schema is expressed as a constrained tool call, so schema-valid output is structural rather than probabilistic.
- §4.4's five layers stay, but as the fallback for when the constraint path is unavailable — a backend that does not implement it, a model without a matching formatter, a constraint that fails to build — not as the primary defence.
- The fine-tune is still done, and is still benchmarked, but against a constrained few-shot baseline on the frozen held-out split. Whichever wins on the numbers is what ships.
- **LoRA stops being a requirement**, which reopens T039. A model no longer has to support adapters to be a candidate.

## Consequences

- **ADR-0003's accepted cost is reversed, and its decision is vindicated rather than overturned.** MediaPipe is still the right choice; the one thing it was giving up turned out to be temporary. 0003 keeps its status and gains a dated note. This ADR does not supersede it.
- **The section 06 deadlock breaks.** T039 was stuck between a model that breaches the memory budget and a model that cannot be fine-tuned. Freed from LoRA, the small-model line becomes viable: a 270M Gemma is 249 MB at q4, can be **fully** fine-tuned on free hardware with no adapter and no GPU-only constraint, and Google publishes `FunctionGemma-270M-Mobile-Actions`, a Gemma fine-tuned for mobile action extraction, which is this project's task almost exactly. T037's LoRA gate stops being able to sink the section, because a negative result no longer costs the fine-tune.
- **The portfolio story gets stronger, not weaker.** "I fine-tuned a model" is common. "I made malformed output structurally impossible, then fine-tuned a model to see whether it beat the constrained baseline, measured both on a frozen split, and shipped the winner" is an engineering story with a number attached. T083's comparison table gains a row and becomes the artifact rather than the appendix.
- **Constrained decoding is probably GPU-only.** The native library has `TfLiteOpenClExternalOutputHandler::UpdateConstraintMask` and an error reading `UpdateConstraintMask not implemented for this backend`. So this does not remove the GPU dependency, it moves it. It also cannot be tested on an emulator, which T039 already established has no OpenCL at all.
- **It does not solve calibration, and must not be read as doing so.** A constrained `confidence` field is still a number the model invents; constraining the *shape* of an answer says nothing about whether it is *right*. T035's threshold and the abstention path stay exactly as they are, and §4.3's `unknown` intent stays a first-class output. The repair pass becoming less load-bearing is not licence to trust the content.
- **Costs to carry:** `localagents-fc` is at version 0.1.0. It ships native libraries for arm64-v8a and x86_64 only, which happen to be exactly the ABIs this project already filters to, adding roughly 4 MB. It pulls `org.json` into production, where this project currently has it as a test-only dependency and Android ships its own in the framework, so that needs checking. It also moves Guava from 27.0.1-android to 33.3.1-android.
- **None of this has been run.** Every claim about the API surface is read off published artifacts and the shipped native library. T190 is the task that proves it on hardware, and until it does, this ADR records a decision made on strong evidence rather than on a demonstration.

## Alternatives considered

- **Keep fine-tuning as the mechanism and skip constrained decoding.** This is the plan of record, and it is what had T039 deadlocked. It also means the launcher's output validity rests on a training run, which is a weaker guarantee than a decoder that cannot emit the wrong token.
- **Adopt constrained decoding and drop fine-tuning entirely.** Cheaper, and defensible on pure engineering grounds now that validity is structural. Rejected because the fine-tune-to-benchmark pipeline is a stated first-class goal of the project, and because the comparison is worth more than either result alone: shipping the constrained baseline is only interesting if you can say what the fine-tune would have bought.
- **Wait for the LiteRT-LM migration and get constrained decoding there.** Tempting, since MediaPipe is maintenance-only and `litert-community` publishes actively (`litertlm-android` is at 0.17.1 while `tasks-genai` has not moved since 0.10.35 in April). Rejected as sequencing: the constraint API is reachable today from the runtime already shipped, and T029 established that MediaPipe's LLM stack is already LiteRT-LM underneath, so this work is unlikely to be thrown away by that migration. Revisit at T091/T092.
