package com.bysel.trader.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

private const val TAG = "OnDeviceLlm"
private const val MODEL_FILENAME = "gemma_2b_it_cpu_int4.bin"
private const val MODEL_URL =
    "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma_2b_it_cpu_int4/float32/1/gemma_2b_it_cpu_int4.bin"
private const val MODEL_MIN_BYTES = 100_000_000L  // sanity check: > 100 MB = valid download

sealed class LlmDownloadState {
    object NotDownloaded : LlmDownloadState()
    data class Downloading(val progressPct: Int) : LlmDownloadState()
    object Initializing : LlmDownloadState()
    object Ready : LlmDownloadState()
    data class Error(val message: String) : LlmDownloadState()
}

object OnDeviceLlmManager {

    private var inference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    private val _state = MutableStateFlow<LlmDownloadState>(LlmDownloadState.NotDownloaded)
    val state: StateFlow<LlmDownloadState> = _state

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_FILENAME)

    fun isModelDownloaded(context: Context): Boolean {
        val f = modelFile(context)
        return f.exists() && f.length() > MODEL_MIN_BYTES
    }

    fun isReady(): Boolean = inference != null && session != null

    suspend fun initialize(context: Context) {
        if (inference != null && session != null) return
        _state.value = LlmDownloadState.Initializing
        withContext(Dispatchers.Default) {
            try {
                // Engine options (tasks-genai 0.10.35+): sampling knobs live on the session.
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelFile(context).absolutePath)
                    .setMaxTokens(512)
                    .setMaxTopK(40)
                    .build()
                val engine = LlmInference.createFromOptions(context, options)
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.4f)
                    .setRandomSeed(42)
                    .build()
                session?.close()
                inference?.close()
                inference = engine
                session = LlmInferenceSession.createFromOptions(engine, sessionOptions)
                _state.value = LlmDownloadState.Ready
                Log.i(TAG, "On-device LLM ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init LLM: ${e.message}")
                runCatching { session?.close() }
                runCatching { inference?.close() }
                session = null
                inference = null
                _state.value = LlmDownloadState.Error("Init failed: ${e.message}")
            }
        }
    }

    suspend fun downloadModel(context: Context) {
        if (isModelDownloaded(context)) {
            initialize(context)
            return
        }
        withContext(Dispatchers.IO) {
            val dest = modelFile(context)
            val tmp = File(context.filesDir, "$MODEL_FILENAME.tmp")
            try {
                val conn = URL(MODEL_URL).openConnection()
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.connect()
                val total = conn.contentLengthLong

                conn.getInputStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(65_536)
                        var downloaded = 0L
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            output.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val pct = ((downloaded * 100) / total).toInt()
                                _state.value = LlmDownloadState.Downloading(pct)
                            }
                        }
                    }
                }
                tmp.renameTo(dest)
                Log.i(TAG, "Model downloaded: ${dest.length()} bytes")
            } catch (e: Exception) {
                tmp.delete()
                Log.e(TAG, "Download failed: ${e.message}")
                _state.value = LlmDownloadState.Error("Download failed: ${e.message}")
                return@withContext
            }
        }
        initialize(context)
    }

    fun generateResponse(prompt: String): String? {
        val activeSession = session
        return try {
            if (activeSession != null) {
                activeSession.addQueryChunk(prompt)
                activeSession.generateResponse()
            } else {
                inference?.generateResponse(prompt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            null
        }
    }

    fun buildPrompt(query: String, stockContext: String?): String {
        val ctx = if (!stockContext.isNullOrBlank()) "$stockContext\n\n" else ""
        return "<start_of_turn>user\n${ctx}You are BYSEL AI, an expert Indian stock market analyst. Answer concisely.\n\nQuestion: $query<end_of_turn>\n<start_of_turn>model\n"
    }
}
