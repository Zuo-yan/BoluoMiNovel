package com.novelai.assistant.data.network

import com.novelai.assistant.data.repository.ApiProviderConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** 合成后的音频字节流及其容器元数据 */
data class SynthesizedAudio(
    val bytes: ByteArray,
    val extension: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesizedAudio) return false
        return extension == other.extension && mimeType == other.mimeType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + extension.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}

/** 云端 AI TTS 可选音色条目 */
data class AiTtsVoice(
    val id: String,
    val displayName: String,
    val description: String = "",
    val category: String = ""
)

/**
 * BYOK 云端 TTS 语音合成客户端：
 * - 支持 Google Gemini 原生 TTS（Interactions API 3.8+ 与 GenerateContent 2.5+/中转兼容协议）
 * - 支持 OpenAI /v1/audio/speech 兼容协议（覆盖 OpenAI、OneAPI/NewAPI、硅基流动、自建语音网关等）
 * - 自动将 Gemini 返回的裸 PCM (audio/L16) 封装为标准 RIFF/WAV，保证 MediaPlayer 可直接播放与变速
 */
@Singleton
class TtsSynthesisClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json
) {
    suspend fun synthesize(
        config: ApiProviderConfig,
        model: String,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) {
            return Result.failure(IllegalArgumentException("待合成文本不能为空"))
        }
        val cleanModel = model.trim().ifBlank { config.selectedModel.trim() }
        if (cleanModel.isBlank()) {
            return Result.failure(IllegalArgumentException("TTS 模型名不能为空"))
        }
        if (config.kind == ProviderKind.ANTHROPIC) {
            return Result.failure(IllegalStateException("Anthropic Messages 协议暂不支持语音合成接口"))
        }

        val resolvedVoice = voice.trim().ifBlank { defaultVoiceFor(config) }
        return try {
            when {
                config.kind == ProviderKind.GOOGLE_GEMINI ->
                    synthesizeGemini(config, cleanModel, cleanText, resolvedVoice)
                isDeepSeekTts(config, cleanModel) ->
                    synthesizeDeepSeek(config, cleanText, resolvedVoice)
                config.kind == ProviderKind.OPENAI_COMPATIBLE || config.kind == ProviderKind.OPENAI_RESPONSES ->
                    synthesizeOpenAiCompatible(config, cleanModel, cleanText, resolvedVoice)
                else -> Result.failure(IllegalStateException("Anthropic 协议不支持语音合成"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /** 连通性测试：合成短句并返回耗时（毫秒） */
    suspend fun test(config: ApiProviderConfig, model: String, voice: String = ""): Result<Long> {
        val started = System.currentTimeMillis()
        return synthesize(config, model, "你好，这是语音合成连通性测试。", voice).map { audio ->
            check(audio.bytes.isNotEmpty()) { "返回音频数据为空" }
            System.currentTimeMillis() - started
        }
    }

    /** 获取服务商可用的 AI 音色列表；DeepSeek 返回内置官方音色，Gemini 会尝试拉取 /voices 扩展库 */
    suspend fun listVoices(config: ApiProviderConfig): List<AiTtsVoice> {
        if (ModelMetadataClient.isDeepSeekProvider(config)) {
            return DEEPSEEK_VOICES
        }
        if (config.kind != ProviderKind.GOOGLE_GEMINI) {
            return OPENAI_COMPAT_VOICES
        }
        val remote = runCatching {
            val base = AiProtocol.base(config)
            val response = withTimeoutOrNull(12_000L) {
                httpClient.get("$base/voices?page_size=100") {
                    AiProtocol.headers(config).forEach { (k, v) -> headers.append(k, v) }
                }
            } ?: return@runCatching emptyList()
            if (response.status.value !in 200..299) return@runCatching emptyList()
            val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
            parseGeminiVoicesResponse(root)
        }.getOrDefault(emptyList())

        return (GEMINI_STUDIO_VOICES + remote).distinctBy { it.id.lowercase() }
    }

    private suspend fun synthesizeGemini(
        config: ApiProviderConfig,
        model: String,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val cleanModel = model.removePrefix("models/")
        if (shouldPreferGeminiInteractions(cleanModel)) {
            val interactionsResult = requestGeminiInteractions(config, cleanModel, text, voice)
            if (interactionsResult.isSuccess) return interactionsResult
            val failure = interactionsResult.exceptionOrNull()
            // 中转站或旧版本入口未转发 /interactions（通常返回 404/405/501）时，自动降级尝试 :generateContent
            val msg = failure?.message.orEmpty()
            val canFallback = msg.contains("HTTP 404") ||
                msg.contains("HTTP 405") ||
                msg.contains("HTTP 501") ||
                msg.contains("404")
            if (!canFallback) {
                return interactionsResult
            }
        }
        return requestGeminiGenerateContent(config, cleanModel, text, voice)
    }

    private suspend fun postJson(
        endpoint: String,
        config: ApiProviderConfig,
        body: JsonObject
    ): HttpResponse? = withTimeoutOrNull(TTS_TIMEOUT_MS) {
        httpClient.post(endpoint) {
            AiProtocol.headers(config)
                .filterKeys { !it.equals("Content-Type", ignoreCase = true) }
                .forEach { (k, v) -> headers.append(k, v) }
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }
    }

    private suspend fun requestGeminiInteractions(
        config: ApiProviderConfig,
        model: String,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val endpoint = "${AiProtocol.base(config)}/interactions"
        val body = buildGeminiInteractionsBody(model, text, voice)
        val response = postJson(endpoint, config, body)
            ?: return Result.failure(IllegalStateException("Gemini 语音合成请求超时"))

        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) {
            return Result.failure(IllegalStateException(AiErrorParser.parse(response.status.value, raw, config.kind.label)))
        }
        val root = json.parseToJsonElement(raw).jsonObject
        val audio = parseGeminiInteractionsAudio(root)
            ?: return Result.failure(IllegalStateException("Gemini Interactions 响应中未找到有效音频数据"))
        return Result.success(audio)
    }

    private suspend fun requestGeminiGenerateContent(
        config: ApiProviderConfig,
        model: String,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val endpoint = "${AiProtocol.base(config)}/models/${encodePath(model)}:generateContent"
        val body = buildGeminiGenerateContentBody(text, voice)
        val response = postJson(endpoint, config, body)
            ?: return Result.failure(IllegalStateException("Gemini 语音合成请求超时"))

        val raw = response.bodyAsText()
        if (response.status.value !in 200..299) {
            return Result.failure(IllegalStateException(AiErrorParser.parse(response.status.value, raw, config.kind.label)))
        }
        val root = json.parseToJsonElement(raw).jsonObject
        val audio = parseGeminiGenerateContentAudio(root)
            ?: return Result.failure(IllegalStateException(buildGeminiMissingAudioMessage(root)))
        return Result.success(audio)
    }

    private suspend fun synthesizeOpenAiCompatible(
        config: ApiProviderConfig,
        model: String,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val endpoint = "${AiProtocol.base(config)}/audio/speech"
        val body = buildOpenAiSpeechBody(model, text, voice)
        val response = postJson(endpoint, config, body)
            ?: return Result.failure(IllegalStateException("语音合成请求超时"))

        if (response.status.value !in 200..299) {
            val errBody = runCatching { response.bodyAsText() }.getOrDefault("")
            return Result.failure(IllegalStateException(AiErrorParser.parse(response.status.value, errBody, config.kind.label)))
        }

        val bytes = response.bodyAsBytes()
        if (bytes.isEmpty()) {
            return Result.failure(IllegalStateException("服务端返回了空的音频流"))
        }
        val contentType = response.contentType()?.toString().orEmpty().lowercase()
        validateAudioPayload(contentType, bytes, config.kind.label)?.let { msg ->
            return Result.failure(IllegalStateException(msg))
        }
        val ext = detectAudioExtension(contentType, bytes)
        val mime = when (ext) {
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
        return Result.success(
            SynthesizedAudio(
                bytes = if (ext == "wav") repairStreamingWavHeaderIfNeeded(bytes) else bytes,
                extension = ext,
                mimeType = mime
            )
        )
    }

    private suspend fun synthesizeDeepSeek(
        config: ApiProviderConfig,
        text: String,
        voice: String
    ): Result<SynthesizedAudio> {
        val endpoint = deepSeekTtsEndpoint(config)
        val safeVoice = if (voice in setOf("stella", "tide")) voice else "stella"
        val body = buildDeepSeekTtsBody(text, safeVoice)
        val response = postJson(endpoint, config, body)
            ?: return Result.failure(IllegalStateException("DeepSeek 语音合成请求超时"))

        if (response.status.value !in 200..299) {
            val errBody = runCatching { response.bodyAsText() }.getOrDefault("")
            return Result.failure(IllegalStateException(AiErrorParser.parse(response.status.value, errBody, "DeepSeek TTS")))
        }

        val bytes = response.bodyAsBytes()
        if (bytes.isEmpty()) {
            return Result.failure(IllegalStateException("DeepSeek 服务端返回了空的音频流"))
        }
        val contentType = response.contentType()?.toString().orEmpty().lowercase()
        validateAudioPayload(contentType, bytes, "DeepSeek TTS")?.let { msg ->
            return Result.failure(IllegalStateException(msg))
        }
        // DeepSeek 请求 format="pcm" 时返回 24kHz 16-bit 单声道裸 PCM，统一封装成标准 44 字节 WAV 确保 MediaPlayer 稳定解码；
        // 若代理或兼容层仍返回 RIFF/WAV 或其他容器格式，则自动检测并修正流式 WAV 占位长度。
        if (hasRiffHeader(bytes) || contentType.contains("wav")) {
            val wavBytes = if (hasRiffHeader(bytes)) {
                repairStreamingWavHeaderIfNeeded(bytes)
            } else {
                wrapPcmAsWav(bytes, DEFAULT_PCM_SAMPLE_RATE)
            }
            return Result.success(SynthesizedAudio(bytes = wavBytes, extension = "wav", mimeType = "audio/wav"))
        }
        if (contentType.contains("pcm") || contentType.contains("l16") ||
            (contentType.isBlank() || contentType.contains("octet-stream"))
        ) {
            // 若字节头不是常见压缩音频容器魔数（MP3 ID3/ADTS、Ogg、FLAC），均按 24kHz PCM 封装为标准 WAV
            val container = detectExplicitContainerMagic(bytes)
            if (container == null) {
                return Result.success(
                    SynthesizedAudio(
                        bytes = wrapPcmAsWav(bytes, DEFAULT_PCM_SAMPLE_RATE),
                        extension = "wav",
                        mimeType = "audio/wav"
                    )
                )
            }
            val mime = if (container == "ogg") "audio/ogg" else "audio/$container"
            return Result.success(SynthesizedAudio(bytes = bytes, extension = container, mimeType = mime))
        }
        val ext = detectAudioExtension(contentType, bytes)
        val mime = when (ext) {
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
        return Result.success(SynthesizedAudio(bytes = bytes, extension = ext, mimeType = mime))
    }

    companion object {
        private const val TTS_TIMEOUT_MS = 45_000L
        private const val DEFAULT_PCM_SAMPLE_RATE = 24_000

        /** Gemini 官方 30 个预置 Studio 音色 */
        val GEMINI_STUDIO_VOICES: List<AiTtsVoice> = listOf(
            AiTtsVoice("Kore", "Kore · 坚定沉稳", "Firm · 适合旁白与正剧叙述", "Gemini Studio"),
            AiTtsVoice("Zephyr", "Zephyr · 明快清朗", "Bright · 节奏轻快自然", "Gemini Studio"),
            AiTtsVoice("Puck", "Puck · 灵动活泼", "Upbeat · 富有活力与表现力", "Gemini Studio"),
            AiTtsVoice("Charon", "Charon · 儒雅稳重", "Informative · 清晰耐听", "Gemini Studio"),
            AiTtsVoice("Aoede", "Aoede · 清风舒朗", "Breezy · 轻松柔和", "Gemini Studio"),
            AiTtsVoice("Sulafat", "Sulafat · 温润亲切", "Warm · 温暖长篇伴读", "Gemini Studio"),
            AiTtsVoice("Leda", "Leda · 青春轻盈", "Youthful · 少女/青春感", "Gemini Studio"),
            AiTtsVoice("Fenrir", "Fenrir · 激昂富有张力", "Excitable · 热血战斗场景", "Gemini Studio"),
            AiTtsVoice("Vindemiatrix", "Vindemiatrix · 温柔细腻", "Gentle · 温情慢读", "Gemini Studio"),
            AiTtsVoice("Gacrux", "Gacrux · 成熟厚重", "Mature · 历史悬疑叙事", "Gemini Studio"),
            AiTtsVoice("Orus", "Orus · 果断有力", "Firm · 沉稳男声", "Gemini Studio"),
            AiTtsVoice("Callirrhoe", "Callirrhoe · 从容随性", "Easy-going · 日常轻小说", "Gemini Studio"),
            AiTtsVoice("Autonoe", "Autonoe · 明亮清晰", "Bright · 通透自然", "Gemini Studio"),
            AiTtsVoice("Enceladus", "Enceladus · 气声低语", "Breathy · 沉浸氛围感", "Gemini Studio"),
            AiTtsVoice("Iapetus", "Iapetus · 吐字清晰", "Clear · 标准播讲", "Gemini Studio"),
            AiTtsVoice("Umbriel", "Umbriel · 舒缓自然", "Easy-going · 睡前听书", "Gemini Studio"),
            AiTtsVoice("Algieba", "Algieba · 平滑流畅", "Smooth · 连贯长句", "Gemini Studio"),
            AiTtsVoice("Despina", "Despina · 柔和顺滑", "Smooth · 细腻流畅", "Gemini Studio"),
            AiTtsVoice("Erinome", "Erinome · 清澈朗润", "Clear · 明晰声线", "Gemini Studio"),
            AiTtsVoice("Algenib", "Algenib · 沙哑质感", "Gravelly · 沧桑角色感", "Gemini Studio"),
            AiTtsVoice("Rasalgethi", "Rasalgethi · 叙述感强", "Informative · 娓娓道来", "Gemini Studio"),
            AiTtsVoice("Laomedeia", "Laomedeia · 明朗上扬", "Upbeat · 轻快叙述", "Gemini Studio"),
            AiTtsVoice("Achernar", "Achernar · 轻柔舒缓", "Soft · 温柔女声", "Gemini Studio"),
            AiTtsVoice("Alnilam", "Alnilam · 稳重有力", "Firm · 正统旁白", "Gemini Studio"),
            AiTtsVoice("Schedar", "Schedar · 平稳均衡", "Even · 长时间听不累", "Gemini Studio"),
            AiTtsVoice("Pulcherrima", "Pulcherrima · 饱满直率", "Forward · 感染力强", "Gemini Studio"),
            AiTtsVoice("Achird", "Achird · 友好亲和", "Friendly · 轻松口吻", "Gemini Studio"),
            AiTtsVoice("Zubenelgenubi", "Zubenelgenubi · 随和自然", "Casual · 对话感强", "Gemini Studio"),
            AiTtsVoice("Sadachbia", "Sadachbia · 生动鲜活", "Lively · 富于起伏", "Gemini Studio"),
            AiTtsVoice("Sadaltager", "Sadaltager · 博学沉静", "Knowledgeable · 推理/科普", "Gemini Studio")
        )

        /** OpenAI /v1/audio/speech 标准预设音色 */
        val OPENAI_COMPAT_VOICES: List<AiTtsVoice> = listOf(
            AiTtsVoice("alloy", "alloy · 中性均衡", "通用标准旁白音色", "OpenAI 标准"),
            AiTtsVoice("echo", "echo · 沉稳男声", "低沉清晰，适合男频/悬疑", "OpenAI 标准"),
            AiTtsVoice("fable", "fable · 故事叙事", "富有故事感与抑扬顿挫", "OpenAI 标准"),
            AiTtsVoice("onyx", "onyx · 厚重磁性", "稳重深沉，适合史诗/正剧", "OpenAI 标准"),
            AiTtsVoice("nova", "nova · 活力女声", "清亮自然，适合轻小说/都市", "OpenAI 标准"),
            AiTtsVoice("shimmer", "shimmer · 柔和清澈", "温婉舒适，适合长期伴读", "OpenAI 标准"),
            AiTtsVoice("ash", "ash · 干脆利落", "现代感强", "OpenAI 扩展"),
            AiTtsVoice("ballad", "ballad · 抒情叙事", "情感细腻", "OpenAI 扩展"),
            AiTtsVoice("coral", "coral · 温暖明快", "亲切自然", "OpenAI 扩展"),
            AiTtsVoice("sage", "sage · 冷静知性", "克制清晰", "OpenAI 扩展")
        )

        /** DeepSeek 官方 Tool TTS 音色 */
        val DEEPSEEK_VOICES: List<AiTtsVoice> = listOf(
            AiTtsVoice("stella", "stella · 海星女声", "DeepSeek 官方默认 · 清晰自然多语种", "DeepSeek 官方"),
            AiTtsVoice("tide", "tide · 暗潮男声", "DeepSeek 官方 · 沉稳质感叙事", "DeepSeek 官方")
        )

        fun defaultVoiceFor(kind: ProviderKind): String = when (kind) {
            ProviderKind.GOOGLE_GEMINI -> "Kore"
            else -> "alloy"
        }

        fun defaultVoiceFor(config: ApiProviderConfig): String = when {
            config.kind == ProviderKind.GOOGLE_GEMINI -> "Kore"
            ModelMetadataClient.isDeepSeekProvider(config) -> "stella"
            else -> "alloy"
        }

        fun recommendedTtsModels(config: ApiProviderConfig): List<String> {
            val host = runCatching { java.net.URI(config.baseUrl).host }.getOrNull().orEmpty().lowercase()
            return when {
                config.kind == ProviderKind.GOOGLE_GEMINI || config.presetId == "google" || host.contains("googleapis") ->
                    listOf("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts", "gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts")
                ModelMetadataClient.isDeepSeekProvider(config) ->
                    listOf(ModelMetadataClient.DEEPSEEK_TTS_VIRTUAL_MODEL)
                config.presetId == "openai" || host.contains("openai.com") ->
                    listOf("gpt-4o-mini-tts", "tts-1", "tts-1-hd")
                config.presetId == "siliconflow" || host.contains("siliconflow") ->
                    listOf("FunAudioLLM/CosyVoice2-0.5B", "IndexTeam/IndexTTS-2", "fishaudio/fish-speech-1.5")
                config.kind == ProviderKind.ANTHROPIC -> emptyList()
                else ->
                    listOf("tts-1", "gpt-4o-mini-tts", "FunAudioLLM/CosyVoice2-0.5B", "kokoro")
            }
        }

        internal fun isDeepSeekTts(config: ApiProviderConfig, model: String): Boolean {
            if (ModelMetadataClient.isDeepSeekProvider(config)) return true
            return model.trim().equals(ModelMetadataClient.DEEPSEEK_TTS_VIRTUAL_MODEL, ignoreCase = true)
        }

        internal fun deepSeekTtsEndpoint(config: ApiProviderConfig): String {
            var base = AiProtocol.base(config).trimEnd('/')
            if (!base.endsWith("/v1")) {
                base += "/v1"
            }
            return "$base/tools/tts"
        }

        internal fun buildDeepSeekTtsBody(text: String, voiceId: String): JsonObject =
            buildJsonObject {
                put("text", text)
                put("voice_id", voiceId)
                put("format", "pcm")
                put("filter_markdown", true)
            }

        internal fun shouldPreferGeminiInteractions(model: String): Boolean {
            val lower = model.lowercase()
            return lower.contains("3.8") || lower.contains("3.1")
        }

        internal fun buildGeminiInteractionsBody(model: String, text: String, voice: String): JsonObject =
            buildJsonObject {
                put("model", model)
                putJsonArray("input") {
                    add(
                        buildJsonObject {
                            put("type", "user_input")
                            putJsonArray("content") {
                                add(
                                    buildJsonObject {
                                        put("type", "text")
                                        put("text", text)
                                    }
                                )
                            }
                        }
                    )
                }
                put("response_format", buildJsonObject { put("type", "audio") })
                put(
                    "generation_config",
                    buildJsonObject {
                        putJsonArray("speech_config") {
                            add(buildJsonObject { put("voice", voice) })
                        }
                    }
                )
            }

        internal fun buildGeminiGenerateContentBody(text: String, voice: String): JsonObject =
            buildJsonObject {
                putJsonArray("contents") {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            putJsonArray("parts") {
                                add(buildJsonObject { put("text", text) })
                            }
                        }
                    )
                }
                put(
                    "generationConfig",
                    buildJsonObject {
                        putJsonArray("responseModalities") { add(kotlinx.serialization.json.JsonPrimitive("AUDIO")) }
                        put(
                            "speechConfig",
                            buildJsonObject {
                                put(
                                    "voiceConfig",
                                    buildJsonObject {
                                        put(
                                            "prebuiltVoiceConfig",
                                            buildJsonObject {
                                                put("voiceName", voice)
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }

        internal fun buildOpenAiSpeechBody(model: String, text: String, voice: String): JsonObject =
            buildJsonObject {
                put("model", model)
                put("input", text)
                put("voice", voice)
                put("response_format", "mp3")
                put("speed", 1.0)
            }

        internal fun parseGeminiInteractionsAudio(root: JsonObject): SynthesizedAudio? {
            val steps = root.list("steps")
            for (stepElem in steps.reversed()) {
                val step = stepElem as? JsonObject ?: continue
                val contents = step.list("content")
                for (cElem in contents) {
                    val part = cElem as? JsonObject ?: continue
                    if (part.str("type") == "audio") {
                        val b64 = part.str("data")
                        if (b64.isNotBlank()) {
                            val rawBytes = Base64.getDecoder().decode(b64.trim())
                            val mime = part.str("mime_type").ifBlank { "audio/wav" }
                            return normalizeAudioPayload(rawBytes, mime)
                        }
                    }
                }
            }
            // 兼容直接返回 output_audio 的简化结构
            val outputAudio = root["output_audio"] as? JsonObject
            val b64 = outputAudio?.str("data").orEmpty()
            if (b64.isNotBlank()) {
                val rawBytes = Base64.getDecoder().decode(b64.trim())
                val mime = outputAudio?.str("mime_type").orEmpty().ifBlank { "audio/wav" }
                return normalizeAudioPayload(rawBytes, mime)
            }
            return null
        }

        internal fun parseGeminiGenerateContentAudio(root: JsonObject): SynthesizedAudio? {
            val candidates = root.list("candidates")
            for (cand in candidates) {
                val content = (cand as? JsonObject)?.node("content") ?: continue
                for (partElem in content.list("parts")) {
                    val part = partElem as? JsonObject ?: continue
                    val inline = (part["inlineData"] as? JsonObject) ?: (part["inline_data"] as? JsonObject) ?: continue
                    val b64 = inline.str("data")
                    if (b64.isNotBlank()) {
                        val rawBytes = Base64.getDecoder().decode(b64.trim())
                        val mime = inline.str("mimeType").ifBlank { inline.str("mime_type") }.ifBlank { "audio/L16;rate=24000" }
                        return normalizeAudioPayload(rawBytes, mime)
                    }
                }
            }
            return null
        }

        internal fun parseGeminiVoicesResponse(root: JsonObject): List<AiTtsVoice> {
            val list = root.list("voices")
            return list.mapNotNull { elem ->
                val obj = elem as? JsonObject ?: return@mapNotNull null
                val id = obj.str("id").ifBlank { obj.str("name") }.trim()
                if (id.isBlank()) return@mapNotNull null
                val display = obj.str("display_name").ifBlank { obj.str("displayName") }.ifBlank { id }
                val lang = obj.str("language_code").ifBlank { obj.str("languageCode") }
                val gender = obj.str("gender")
                val desc = obj.str("description")
                val meta = listOf(lang, gender, desc).filter { it.isNotBlank() }.joinToString(" · ")
                AiTtsVoice(
                    id = id,
                    displayName = display,
                    description = meta,
                    category = "Gemini 扩展音色"
                )
            }
        }

        internal fun normalizeAudioPayload(rawBytes: ByteArray, mimeType: String): SynthesizedAudio {
            val lower = mimeType.lowercase()
            val hasRiffHeader = rawBytes.size >= 12 &&
                rawBytes[0] == 'R'.code.toByte() &&
                rawBytes[1] == 'I'.code.toByte() &&
                rawBytes[2] == 'F'.code.toByte() &&
                rawBytes[3] == 'F'.code.toByte()
            if (hasRiffHeader) {
                return SynthesizedAudio(repairStreamingWavHeaderIfNeeded(rawBytes), "wav", "audio/wav")
            }
            if (lower.contains("mp3") || lower.contains("mpeg")) {
                return SynthesizedAudio(rawBytes, "mp3", "audio/mpeg")
            }
            if (lower.contains("ogg") || lower.contains("opus")) {
                return SynthesizedAudio(rawBytes, "ogg", "audio/ogg")
            }
            if (lower.contains("l16") || lower.contains("pcm") || lower.contains("wav")) {
                val sampleRate = Regex("""rate=(\d+)""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: DEFAULT_PCM_SAMPLE_RATE
                return SynthesizedAudio(wrapPcmAsWav(rawBytes, sampleRate), "wav", "audio/wav")
            }
            val ext = detectAudioExtension(lower, rawBytes)
            return SynthesizedAudio(rawBytes, ext, mimeType.ifBlank { "audio/$ext" })
        }

        internal fun detectAudioExtension(contentType: String, bytes: ByteArray): String {
            if (bytes.size >= 4) {
                if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                    bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()
                ) return "wav"
                if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
                    bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
                ) return "ogg"
                if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                    bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
                ) return "flac"
            }
            return when {
                contentType.contains("wav") -> "wav"
                contentType.contains("ogg") || contentType.contains("opus") -> "ogg"
                contentType.contains("aac") -> "aac"
                contentType.contains("flac") -> "flac"
                else -> "mp3"
            }
        }

        internal fun buildGeminiMissingAudioMessage(root: JsonObject): String {
            val blockReason = root.node("promptFeedback").str("blockReason")
            if (blockReason.isNotBlank()) {
                return "Gemini 拒绝生成语音（blockReason=$blockReason）"
            }
            val firstCand = root.list("candidates").firstOrNull() as? JsonObject
            val finishReason = firstCand?.str("finishReason").orEmpty()
            val fallbackText = firstCand?.node("content")?.list("parts")
                ?.mapNotNull { (it as? JsonObject)?.str("text")?.takeIf { t -> t.isNotBlank() } }
                ?.joinToString(" ")
                ?.take(120)
                .orEmpty()
            return buildString {
                append("Gemini 响应中未包含音频 inlineData")
                if (finishReason.isNotBlank()) append("（finishReason=$finishReason）")
                if (fallbackText.isNotBlank()) append("：$fallbackText")
            }
        }

        internal fun hasRiffHeader(bytes: ByteArray): Boolean =
            bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()

        private fun detectExplicitContainerMagic(bytes: ByteArray): String? {
            if (bytes.size < 4) return null
            if (bytes[0] == 'O'.code.toByte() && bytes[1] == 'g'.code.toByte() &&
                bytes[2] == 'g'.code.toByte() && bytes[3] == 'S'.code.toByte()
            ) return "ogg"
            if (bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte()
            ) return "flac"
            if (bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() &&
                bytes[2] == '3'.code.toByte()
            ) return "mp3"
            return null
        }

        /** 校验响应是否为伪装成 200 OK 的 JSON/HTML/纯文本错误，避免写入损坏的音频缓存 */
        internal fun validateAudioPayload(contentType: String, bytes: ByteArray, providerLabel: String): String? {
            val isTextOrJson = contentType.contains("json") ||
                contentType.contains("text/") ||
                contentType.contains("html") ||
                contentType.contains("xml")
            val trimmedPrefix = bytes.take(128).toByteArray().toString(Charsets.UTF_8).trimStart()
            val looksLikeJsonOrHtml = trimmedPrefix.startsWith("{") ||
                trimmedPrefix.startsWith("[") ||
                trimmedPrefix.startsWith("<!doctype", ignoreCase = true) ||
                trimmedPrefix.startsWith("<html", ignoreCase = true)
            if (isTextOrJson || looksLikeJsonOrHtml) {
                val preview = bytes.take(1024).toByteArray().toString(Charsets.UTF_8)
                return AiErrorParser.parse(200, preview, providerLabel)
            }
            return null
        }

        /**
         * 修复流式 TTS 接口返回的 RIFF/WAV 占位长度（例如 0、0xFFFFFFFF、0x7FFFF000 或大于实际字节数），
         * 避免 Android MediaPlayer 解析容器长度失败。
         */
        internal fun repairStreamingWavHeaderIfNeeded(bytes: ByteArray): ByteArray {
            if (!hasRiffHeader(bytes) || bytes.size < 44) return bytes
            val buffer = ByteBuffer.wrap(bytes.copyOf()).order(ByteOrder.LITTLE_ENDIAN)
            val expectedRiffSize = (bytes.size - 8).coerceAtLeast(36)
            val riffSize = buffer.getInt(4)
            var modified = false
            if (riffSize <= 0 || riffSize > expectedRiffSize) {
                buffer.putInt(4, expectedRiffSize)
                modified = true
            }
            var offset = 12
            while (offset + 8 <= bytes.size) {
                val id0 = bytes[offset]
                val id1 = bytes[offset + 1]
                val id2 = bytes[offset + 2]
                val id3 = bytes[offset + 3]
                val chunkLen = buffer.getInt(offset + 4)
                val remaining = (bytes.size - offset - 8).coerceAtLeast(0)
                if (id0 == 'd'.code.toByte() && id1 == 'a'.code.toByte() &&
                    id2 == 't'.code.toByte() && id3 == 'a'.code.toByte()
                ) {
                    if (chunkLen <= 0 || chunkLen > remaining) {
                        buffer.putInt(offset + 4, remaining)
                        modified = true
                    }
                    break
                }
                if (chunkLen <= 0 || offset + 8 + chunkLen > bytes.size) break
                offset += 8 + chunkLen + (chunkLen and 1)
            }
            return if (modified) buffer.array() else bytes
        }

        /** 将 16-bit 小端单声道裸 PCM 字节封装为标准 44 字节 RIFF/WAV 容器 */
        internal fun wrapPcmAsWav(
            pcmData: ByteArray,
            sampleRate: Int = DEFAULT_PCM_SAMPLE_RATE,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val byteRate = sampleRate * channels * (bitsPerSample / 8)
            val blockAlign = channels * (bitsPerSample / 8)
            val dataSize = pcmData.size
            val chunkSize = 36 + dataSize

            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(chunkSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16) // PCM fmt chunk size
            header.putShort(1.toShort()) // AudioFormat = PCM (1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)

            val out = ByteArrayOutputStream(44 + dataSize)
            out.write(header.array())
            out.write(pcmData)
            return out.toByteArray()
        }
    }
}
