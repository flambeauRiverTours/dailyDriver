package com.flambeauriver.dailydriver.slm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type
import com.google.ai.edge.localagents.fc.FstConstraintProvider
import com.google.ai.edge.localagents.fc.proto.ConstraintOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * **A spike for T190, not a suite member.** Answers the one question that decides whether
 * [ADR-0011] is buildable: can a constraint handle be created and applied on *this* device?
 *
 * The risk is specific. `libllm_inference_engine_jni.so` carries
 * `TfLiteOpenClExternalOutputHandler::UpdateConstraintMask` and an error reading
 * `UpdateConstraintMask not implemented for this backend`, so constrained decoding looks
 * OpenCL-only. If it does not apply on an Adreno 650 then the enum-pinned intent ADR-0011 promises
 * is unreachable and T190 falls back to bounding generation instead.
 *
 * Skipped unless a `modelPath` argument is supplied, so it is inert in CI, which has no OpenCL
 * anyway (T039).
 */
@RunWith(AndroidJUnit4::class)
class ConstraintSpikeTest {

    private val modelPath: String?
        get() = InstrumentationRegistry.getArguments().getString("modelPath")

    @Test
    fun aConstraintCanBeBuiltAndApplied() {
        val path = modelPath
        assumeTrue("no modelPath argument supplied; spike skipped", path != null)
        assumeTrue("model not present", File(path!!).isFile)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build(),
        )

        val report = StringBuilder()
        engine.use {
            val spHandle = engine.sentencePieceProcessorHandle
            report.append("spHandle=").append(spHandle).append(' ')

            // Three intents is enough to prove the mechanism; the real builder over all eighteen
            // is only worth writing once this returns a usable handle.
            val tool = Tool.newBuilder()
                .addFunctionDeclarations(declaration("open_app", mapOf("app" to Type.STRING)))
                .addFunctionDeclarations(declaration("web_search", mapOf("query" to Type.STRING)))
                .addFunctionDeclarations(declaration("unknown", emptyMap()))
                .build()

            val constraintHandle = try {
                FstConstraintProvider(spHandle).use { provider ->
                    provider.createConstraint(
                        listOf(tool),
                        ConstraintOptions.newBuilder()
                            .setToolCallOnly(ConstraintOptions.ToolCallOnly.getDefaultInstance())
                            .build(),
                    ).constraintHandle
                }
            } catch (t: Throwable) {
                throw AssertionError("BUILD FAILED ${t.javaClass.name}: ${t.message}", t)
            }
            report.append("constraintHandle=").append(constraintHandle).append(' ')

            val options = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.1f)
                .setTopK(40)
                .setRandomSeed(0)
                .setConstraintHandle(constraintHandle)
                .build()

            var raw: String? = null
            val ms = try {
                measureTimeMillis {
                    LlmInferenceSession.createFromOptions(engine, options).use { session ->
                        session.addQueryChunk("open gmail")
                        raw = session.generateResponse()
                    }
                }
            } catch (t: Throwable) {
                throw AssertionError("$report APPLY FAILED ${t.javaClass.name}: ${t.message}", t)
            }
            report.append("ms=").append(ms).append(" chars=").append(raw?.length).append(' ')
            report.append("RAW>>>").append(raw?.take(400)).append("<<<END")
        }

        throw AssertionError(report.toString())
    }

    @Test
    fun aTextUntilConstraintCanBeBuilt() {
        // The cheaper mode. It only bounds generation rather than pinning the grammar, so it
        // cannot deliver ADR-0011's enum-constrained intent — but it would close the measured
        // latency gap, and it goes through the same FST provider, so it is worth knowing whether
        // it hits the same tokenizer wall.
        val path = modelPath
        assumeTrue("no modelPath argument supplied; spike skipped", path != null)
        assumeTrue("model not present", File(path!!).isFile)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = LlmInference.createFromOptions(
            context,
            LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(512)
                .setMaxTopK(40)
                .setPreferredBackend(LlmInference.Backend.GPU)
                .build(),
        )
        engine.use {
            val handle = try {
                FstConstraintProvider(engine.sentencePieceProcessorHandle).use { provider ->
                    provider.createConstraint(
                        listOf(Tool.getDefaultInstance()),
                        ConstraintOptions.newBuilder()
                            .setTextUntil(
                                ConstraintOptions.TextUntil.newBuilder()
                                    .setStopPhrase("\n")
                            )
                            .build(),
                    ).constraintHandle
                }
            } catch (t: Throwable) {
                throw AssertionError("TEXT_UNTIL BUILD FAILED ${t.javaClass.name}: ${t.message}", t)
            }
            throw AssertionError("TEXT_UNTIL OK handle=$handle")
        }
    }
    private fun declaration(name: String, slots: Map<String, Type>): FunctionDeclaration {
        val parameters = Schema.newBuilder().setType(Type.OBJECT)
        slots.forEach { (slot, type) ->
            parameters.putProperties(slot, Schema.newBuilder().setType(type).build())
        }
        return FunctionDeclaration.newBuilder()
            .setName(name)
            .setDescription("intent $name")
            .setParameters(parameters.build())
            .build()
    }
}
