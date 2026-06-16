package data.remote.ai

import data.model.*
import data.remote.ApiAi
import data.remote.Prompts
import data.remote.ResultApiAI
import data.remote.handleApiAiError
import data.remote.isSafetyError
import data.repository.ServerRepository
import extensions.initHttpClientCIO
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.util.logging.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private const val HEDRA_BASE_URL = "https://api.hedra.com/web-app/public"
private const val KLING_MODEL_SLUG = "fal/kling-v3-standard-i2v"
private const val VEO_FAST_MODEL_SLUG = "fal/veo-3-fast-i2v"
private const val WAIT_10_MINUTES = 10 * 60_000L

private val hedraJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
private data class HedraAssetRequest(
    val name: String,
    val type: String
)

@Serializable
private data class HedraAssetResponse(
    val id: String,
    val name: String? = null,
    val type: String? = null
)

@Serializable
private data class HedraGenerationRequest(
    val type: String = "video",
    @SerialName("model_slug") val modelSlug: String = KLING_MODEL_SLUG,
    @SerialName("start_keyframe_id") val startKeyframeId: String? = null,
    @SerialName("end_keyframe_id") val endKeyframeId: String? = null,
    @SerialName("generated_video_inputs") val generatedVideoInputs: HedraVideoInputs
)

@Serializable
private data class HedraVideoInputs(
    @SerialName("text_prompt") val textPrompt: String,
    @SerialName("aspect_ratio") val aspectRatio: String = "9:16",
    val resolution: String = "720p",
    @SerialName("duration_ms") val durationMs: Int = 5000
)

@Serializable
private data class HedraGenerationResponse(
    val id: String,
    val status: String? = null,
    val error: HedraError? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
private data class HedraStatusResponse(
    val id: String,
    val status: String? = null,
    val progress: Double? = null,
    @SerialName("asset_id") val assetId: String? = null,
    val url: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("streaming_url") val streamingUrl: String? = null,
    val error: HedraError? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
private data class HedraError(
    val type: String? = null,
    val message: String? = null
)

class KlingHedra(
    private val serverRepository: ServerRepository,
    private val settings: Settings,
    private val apiKey: String,
    private val logger: Logger,
    private val modelSlug: String = KLING_MODEL_SLUG,
    private val fallbackApi: ApiAi? = null,
) : ApiAi {
    private val client = initHttpClientCIO()

    private suspend fun uploadImage(imageBytes: ByteArray, filename: String): String {
        val createResponse = client.post("$HEDRA_BASE_URL/assets") {
            header("X-API-Key", apiKey)
            contentType(ContentType.Application.Json)
            setBody(hedraJson.encodeToString(HedraAssetRequest.serializer(), HedraAssetRequest(name = filename, type = "image")))
        }.bodyAsText()

        val asset = hedraJson.decodeFromString<HedraAssetResponse>(createResponse)
        logger.info("uploadImage(), kling-hedra, created asset: ${asset.id}")

        client.post("$HEDRA_BASE_URL/assets/${asset.id}/upload") {
            header("X-API-Key", apiKey)
            setBody(MultiPartFormDataContent(formData {
                append("file", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                })
            }))
        }
        logger.info("uploadImage(), kling-hedra, uploaded asset: ${asset.id}")

        return asset.id
    }

    override suspend fun generateVideo(
        chatMessage: ChatMessage,
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        queueGen: QueueGen,
        kieAi: Boolean,
        retry: Int
    ): ResultApiAI<Any, Any> {
        return try {
            val aspectRatio = when (chatMessage.orientationType) {
                OrientationType.PORTRAIT -> "9:16"
                OrientationType.LANDSCAPE -> "16:9"
            }

            logger.info("generateVideo(), hedra, model: $modelSlug, user: ${user.getId()}, retry: $retry, start")

            serverRepository.images.saveDoubleOriginImages(photo1 = photo1, photo2 = photo2, user = user)
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.GENERATION))

            val startKeyframeId = if (photo1 != null) {
                uploadImage(photo1, "user-photo1.png")
            } else null

            val prompt = Prompts.get(chatMessage, settings)

            val generationRequest = HedraGenerationRequest(
                modelSlug = modelSlug,
                startKeyframeId = startKeyframeId,
                endKeyframeId = null,
                generatedVideoInputs = HedraVideoInputs(
                    textPrompt = prompt,
                    aspectRatio = aspectRatio,
                    resolution = "720p",
                    durationMs = 8000
                )
            )

            var maxAttempts = 5
            var attempt = 0
            var generationResponseText: String

            while (true) {
                try {
                    attempt++
                    val response = client.post("$HEDRA_BASE_URL/generations") {
                        header("X-API-Key", apiKey)
                        contentType(ContentType.Application.Json)
                        setBody(hedraJson.encodeToString(HedraGenerationRequest.serializer(), generationRequest))
                    }
                    generationResponseText = response.bodyAsText()
                    logger.info("generateVideo(), kling-hedra, generation response: $generationResponseText")
                    break
                } catch (e: Exception) {
                    logger.warn("generateVideo(), kling-hedra, user: ${user.getId()}, POST attempt $attempt failed: ${e.message}")
                    if (attempt >= maxAttempts) {
                        serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                        throw e
                    }
                    delay(5000L)
                }
            }

            val generationResponse = try {
                hedraJson.decodeFromString<HedraGenerationResponse>(generationResponseText)
            } catch (e: Exception) {
                logger.info("generateVideo(), kling-hedra, user: ${user.getId()}, Error parsing response: $generationResponseText")
                serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                throw e
            }

            val generationId = generationResponse.id
            logger.info("generateVideo(), kling-hedra, user: ${user.getId()}, generation started: $generationId")

            val startTime = System.currentTimeMillis()
            var latestProgress = -1.0

            while (true) {
                if (System.currentTimeMillis() - startTime > WAIT_10_MINUTES) {
                    serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.TIMEOUT))
                    throw RuntimeException("generateVideo(), kling-hedra, retry: $retry, video generation timeout")
                }

                val statusText = try {
                    client.get("$HEDRA_BASE_URL/generations/$generationId/status") {
                        header("X-API-Key", apiKey)
                    }.bodyAsText()
                } catch (e: Exception) {
                    logger.warn("generateVideo(), kling-hedra, poll error: ${e.message}")
                    delay(5000L)
                    continue
                }

                val statusResponse = try {
                    hedraJson.decodeFromString<HedraStatusResponse>(statusText)
                } catch (e: Exception) {
                    logger.warn("generateVideo(), kling-hedra, status parse error, raw: $statusText")
                    delay(5000L)
                    continue
                }

                when (statusResponse.status) {
                    "complete" -> {
                        val downloadUrl = statusResponse.downloadUrl
                            ?: statusResponse.url
                            ?: throw RuntimeException("generateVideo(), kling-hedra, no download URL in response")

                        val tempDir = File("raw/tmp")
                        tempDir.mkdirs()
                        val tempVideoFile = File(tempDir, "$generationId.mp4")

                        val channel = client.get(downloadUrl).bodyAsChannel()
                        tempVideoFile.outputStream().use { out ->
                            val buffer = ByteArray(8 * 1024)
                            while (!channel.isClosedForRead) {
                                val bytesRead = channel.readAvailable(buffer)
                                if (bytesRead > 0) out.write(buffer, 0, bytesRead)
                            }
                        }

                        try {
                            serverRepository.videos.saveGeneratedVideo(
                                idVideo = generationId,
                                orientationType = chatMessage.orientationType,
                                user = user
                            )
                            val finalVideoFile = serverRepository.videosFiles.saveGeneratedVideo(
                                videoFile = tempVideoFile,
                                videoId = generationId,
                                user = user
                            )
                            serverRepository.images.generateThumbnail(
                                videoFile = tempVideoFile,
                                idVideo = generationId,
                                user = user
                            )
                            serverRepository.queueGen.upsert(
                                queueGen.copy(idVideo = generationId, status = QueueGenStatus.COMPLETED)
                            )

                            logger.info("generateVideo(), kling-hedra, user: ${user.getId()}, video $generationId completed")
                            return ResultApiAI.Success(data = finalVideoFile)
                        } finally {
                            if (tempVideoFile.exists()) tempVideoFile.delete()
                        }
                    }

                    "error" -> {
                        val errorMsg = statusResponse.errorMessage
                            ?: statusResponse.error?.message
                            ?: "Unknown error"
                        logger.error("generateVideo(), hedra, model: $modelSlug, user: ${user.getId()}, generation $generationId failed: $errorMsg")

                        val queueGenUpd = handleApiAiError(
                            errorMsg = errorMsg,
                            queueGen = queueGen,
                            idVideo = generationId
                        )
                        serverRepository.queueGen.upsert(queueGenUpd)

                        if (fallbackApi != null && !isSafetyError(errorMsg)) {
                            logger.info("generateVideo(), hedra, model: $modelSlug failed, falling back to kling, user: ${user.getId()}")
                            return fallbackApi.generateVideo(chatMessage, photo1, photo2, user, queueGen.copy(status = QueueGenStatus.CREATED), kieAi, 0)
                        }
                        return ResultApiAI.Error(errorMsg)
                    }

                    else -> {
                        val currentProgress = statusResponse.progress ?: latestProgress
                        if (latestProgress < currentProgress) {
                            logger.info(
                                "generateVideo(), kling-hedra, user: ${user.getId()}, " +
                                        "status=${statusResponse.status}, progress=$currentProgress%, waiting..."
                            )
                            latestProgress = currentProgress
                        }
                        delay(5000L)
                    }
                }
            }

            @Suppress("UNREACHABLE_CODE")
            ResultApiAI.Success("generateVideo(), kling-hedra, user: ${user.getId()}, video generated")
        } catch (e: Exception) {
            logger.info("generateVideo(), hedra, model: $modelSlug, user: ${user.getId()}, retry: $retry, Error ${e.stackTraceToString()}")
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
            val error = ResultApiAI.Error(e.message)
            if (fallbackApi != null) {
                logger.info("generateVideo(), hedra, model: $modelSlug failed, falling back to kling, user: ${user.getId()}")
                return fallbackApi.generateVideo(chatMessage, photo1, photo2, user, queueGen.copy(status = QueueGenStatus.CREATED), kieAi, 0)
            }
            error
        }
    }

    override suspend fun getVideo(idVideo: String, user: User): ResultApiAI<Any, Any> {
        return try {
            logger.info("getVideo(), kling-hedra, user: ${user.getId()}, generation: $idVideo")

            val statusText = client.get("$HEDRA_BASE_URL/generations/$idVideo/status") {
                header("X-API-Key", apiKey)
            }.bodyAsText()

            val statusResponse = hedraJson.decodeFromString<HedraStatusResponse>(statusText)

            when (statusResponse.status) {
                "complete" -> {
                    val downloadUrl = statusResponse.downloadUrl
                        ?: statusResponse.url
                        ?: throw RuntimeException("getVideo(), kling-hedra, no download URL")
                    logger.info("getVideo(), kling-hedra, user: ${user.getId()}, video ready")
                    ResultApiAI.Success(data = downloadUrl)
                }

                else -> {
                    val errorMsg = statusResponse.errorMessage
                        ?: statusResponse.error?.message
                        ?: "Video not ready, status: ${statusResponse.status}"
                    ResultApiAI.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            logger.info("getVideo(), kling-hedra, user: ${user.getId()}, Error ${e.stackTraceToString()}")
            ResultApiAI.Error(e.message)
        }
    }

    override fun close() {
        client.close()
    }
}
