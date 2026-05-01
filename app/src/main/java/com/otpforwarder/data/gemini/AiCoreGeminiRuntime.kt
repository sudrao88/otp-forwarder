package com.otpforwarder.data.gemini

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.otpforwarder.domain.classification.GeminiAvailability
import com.otpforwarder.domain.classification.GeminiRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [GeminiRuntime] backed by Android AICore via the ML Kit GenAI Prompt SDK.
 *
 * On devices where AICore is not installed (most non-Pixel/Galaxy hardware) the SDK
 * surfaces this as [FeatureStatus.UNAVAILABLE]; we map any exception during status
 * lookup or model construction to [GeminiAvailability.Unsupported] so the tiered
 * classifier transparently falls back to the keyword tier.
 */
@Singleton
class AiCoreGeminiRuntime @Inject constructor() : GeminiRuntime {

    private val initMutex = Mutex()
    @Volatile private var model: GenerativeModel? = null
    @Volatile private var construction: ConstructionResult = ConstructionResult.NotAttempted

    private val _progress = MutableStateFlow<Float?>(null)
    override val downloadProgress: StateFlow<Float?> = _progress.asStateFlow()

    override suspend fun status(): GeminiAvailability {
        val client = obtainModel() ?: return GeminiAvailability.Unsupported
        return try {
            mapFeatureStatus(client.checkStatus())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "checkStatus failed; treating as unsupported", t)
            GeminiAvailability.Unsupported
        }
    }

    override suspend fun generate(prompt: String): String {
        val client = obtainModel()
            ?: throw IllegalStateException("Gemini Nano runtime unavailable")
        val response = client.generateContent(prompt)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override suspend fun startDownload() {
        val client = obtainModel() ?: return
        try {
            client.download().collect { event ->
                when (event) {
                    is DownloadStatus.DownloadStarted -> {
                        totalBytes = event.bytesToDownload.coerceAtLeast(1L)
                        _progress.value = 0f
                    }
                    is DownloadStatus.DownloadProgress -> {
                        val total = totalBytes
                        if (total > 0L) {
                            _progress.value =
                                (event.totalBytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                    is DownloadStatus.DownloadCompleted -> {
                        _progress.value = 1f
                    }
                    is DownloadStatus.DownloadFailed -> {
                        Log.w(TAG, "Gemini Nano download failed", event.e)
                        _progress.value = null
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "Gemini Nano download threw", t)
            _progress.value = null
        }
    }

    @Volatile private var totalBytes: Long = 0L

    private suspend fun obtainModel(): GenerativeModel? {
        model?.let { return it }
        initMutex.withLock {
            model?.let { return it }
            return when (construction) {
                ConstructionResult.Failed -> null
                ConstructionResult.NotAttempted -> tryConstruct()
            }
        }
    }

    private fun tryConstruct(): GenerativeModel? = try {
        Generation.getClient().also { model = it }
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to construct GenerativeModel; AICore probably unavailable", t)
        construction = ConstructionResult.Failed
        null
    }

    private fun mapFeatureStatus(@FeatureStatus status: Int): GeminiAvailability = when (status) {
        FeatureStatus.AVAILABLE -> GeminiAvailability.Ready
        FeatureStatus.DOWNLOADABLE -> GeminiAvailability.Downloadable
        FeatureStatus.DOWNLOADING -> GeminiAvailability.Downloading
        FeatureStatus.UNAVAILABLE -> GeminiAvailability.Unsupported
        else -> GeminiAvailability.Unknown
    }

    private enum class ConstructionResult { NotAttempted, Failed }

    private companion object {
        const val TAG = "AiCoreGeminiRuntime"
    }
}
