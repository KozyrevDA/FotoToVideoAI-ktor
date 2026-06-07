package data.remote.ai

import app.Constants
import data.model.*
import data.remote.ApiAi
import data.remote.Prompts
import data.remote.ResultApiAI
import data.remote.ai.dto.VideoTaskDto
import data.remote.ai.dto.VideoTaskResultDto
import data.remote.handleApiAiError
import data.repository.ServerRepository
import extensions.initHttpClientCIO
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.delay
import java.io.File

private const val API_ENDPOINT = "api.laozhang.ai/v1"
private const val API_VIDEOS_URL = "https://${API_ENDPOINT}/videos"
private const val SEEDANCE_MODEL_NAME = "seedance-video-api-1-图像转视频生成"
private const val WAIT_10_MINUTES = 10 * 60_000L

class Seedance(
    private val serverRepository: ServerRepository,
    private val settings: Settings,
    private val apiKey: String,
    private val logger: Logger,
) : ApiAi {
    private val client = initHttpClientCIO()

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
            val model = SEEDANCE_MODEL_NAME
            val aspectRatio = when (chatMessage.orientationType) {
                OrientationType.PORTRAIT -> "9:16"
                OrientationType.LANDSCAPE -> "16:9"
            }

            logger.info("generateVideo(), seedance, model: $model, user: ${user.getId()}, retry: $retry, start")

            val parts = buildList {
                add(
                    PartData.FormItem(
                        value = model,
                        dispose = {},
                        partHeaders = headersOf(HttpHeaders.ContentDisposition, "form-data; name=\"model\"")
                    )
                )
                if (photo1 != null) {
                    add(
                        PartData.FileItem(
                            provider = { photo1.inputStream().toByteReadChannel() },
                            dispose = {},
                            partHeaders = headers {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"input_reference\"; filename=\"user-photo1.png\""
                                )
                                append(HttpHeaders.ContentType, "image/png")
                            }
                        )
                    )
                }
                if (photo2 != null) {
                    add(
                        PartData.FileItem(
                            provider = { photo2.inputStream().toByteReadChannel() },
                            dispose = {},
                            partHeaders = headers {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"input_reference\"; filename=\"user-photo2.png\""
                                )
                                append(HttpHeaders.ContentType, "image/png")
                            }
                        )
                    )
                }
                add(
                    PartData.FormItem(
                        value = Prompts.get(chatMessage, settings),
                        dispose = {},
                        partHeaders = headersOf(HttpHeaders.ContentDisposition, "form-data; name=\"prompt\"")
                    )
                )
                add(
                    PartData.FormItem(
                        value = aspectRatio,
                        dispose = {},
                        partHeaders = headersOf(HttpHeaders.ContentDisposition, "form-data; name=\"aspectRatio\"")
                    )
                )
            }

            serverRepository.images.saveDoubleOriginImages(
                photo1 = photo1,
                photo2 = photo2,
                user = user
            )

            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.GENERATION))

            val maxAttempts = 12
            var attempt = 0
            var responseText: String

            while (true) {
                try {
                    attempt++
                    val response = client.post(API_VIDEOS_URL) {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                        setBody(MultiPartFormDataContent(parts))
                    }
                    responseText = response.bodyAsText()
                    break
                } catch (e: Exception) {
                    logger.warn("generateVideo(), seedance, user: ${user.getId()}, POST attempt $attempt failed: ${e.message}")
                    if (attempt >= maxAttempts) {
                        serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                        throw e
                    }
                    delay(5000L)
                }
            }

            val videoTaskDto = try {
                Constants.JSON.decodeFromString<VideoTaskDto>(responseText)
            } catch (e: Exception) {
                logger.info("generateVideo(), seedance, model: $model, user: ${user.getId()}, Error bodyAsText: $responseText")
                serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                throw e
            }
            val startTime = System.currentTimeMillis()
            var latestPercent = -1

            while (true) {
                if (System.currentTimeMillis() - startTime > WAIT_10_MINUTES) {
                    serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.TIMEOUT))
                    throw RuntimeException("generateVideo(), seedance, model: $model, retry: $retry, video generation timeout")
                }

                val videoTaskResultDto = try {
                    client.get("$API_VIDEOS_URL/${videoTaskDto.id}") {
                        header(HttpHeaders.Authorization, "Bearer $apiKey")
                    }.body<VideoTaskResultDto>()
                } catch (e: Exception) {
                    VideoTaskResultDto(id = videoTaskDto.id, status = "in_progress")
                }

                when (videoTaskResultDto.status) {
                    "completed" -> {
                        val contentUrl = "https://api.laozhang.ai/v1/videos/${videoTaskResultDto.id}/content"
                        val tempDir = File("raw/tmp")
                        tempDir.mkdirs()

                        val tempVideoFile = File(tempDir, "${videoTaskDto.id}.mp4")
                        val response = client.get(contentUrl) {
                            header("Authorization", "Bearer $apiKey")
                        }
                        val contentType = response.headers["Content-Type"] ?: ""

                        if (!contentType.contains("video") && !contentType.contains("octet-stream")) {
                            delay(5000L)
                            continue
                        }

                        val channel = response.bodyAsChannel()

                        tempVideoFile.outputStream().use { out ->
                            val buffer = ByteArray(8 * 1024)
                            while (!channel.isClosedForRead) {
                                val bytesRead = channel.readAvailable(buffer)
                                if (bytesRead > 0) out.write(buffer, 0, bytesRead)
                            }
                        }

                        try {
                            serverRepository.videos.saveGeneratedVideo(
                                idVideo = videoTaskResultDto.id,
                                orientationType = chatMessage.orientationType,
                                user = user
                            )
                            val finalVideoFile = serverRepository.videosFiles.saveGeneratedVideo(
                                videoFile = tempVideoFile,
                                videoId = videoTaskResultDto.id,
                                user = user
                            )
                            serverRepository.images.generateThumbnail(
                                videoFile = tempVideoFile,
                                idVideo = videoTaskResultDto.id,
                                user = user
                            )
                            serverRepository.queueGen.upsert(
                                queueGen.copy(
                                    idVideo = videoTaskResultDto.id,
                                    status = QueueGenStatus.COMPLETED
                                )
                            )

                            logger.info("generateVideo(), seedance, model: $model, retry: $retry, user: ${user.getId()}, video id: ${videoTaskResultDto.id}, video generation completed")
                            return ResultApiAI.Success(data = finalVideoFile)
                        } finally {
                            if (tempVideoFile.exists()) {
                                tempVideoFile.delete()
                            }
                        }
                    }

                    "failed" -> {
                        val errorMsg = videoTaskResultDto.error?.message ?: "Unknown error"
                        logger.error("generateVideo(), seedance, model: $model, retry: $retry, user: ${user.getId()}, video id: ${videoTaskResultDto.id}, video generation failed: $errorMsg")

                        val queueGenUpd = handleApiAiError(
                            errorMsg = errorMsg,
                            queueGen = queueGen,
                            idVideo = videoTaskResultDto.id
                        )

                        serverRepository.queueGen.upsert(queueGenUpd)

                        return ResultApiAI.Error(errorMsg)
                    }

                    else -> {
                        val currentPercent = videoTaskResultDto.progress ?: latestPercent

                        if (latestPercent < currentPercent) {
                            logger.info(
                                "generateVideo(), seedance, model: $model, user: ${user.getId()}, retry: $retry, video not ready yet, " +
                                        "status=${videoTaskResultDto.status}, progress=$currentPercent%, waiting..."
                            )
                            latestPercent = currentPercent
                        }
                        delay(5000L)
                    }
                }
            }

            @Suppress("UNREACHABLE_CODE")
            ResultApiAI.Success("generateVideo(), seedance, model: $model, user: ${user.getId()}, video generated successfully")
        } catch (e: Exception) {
            logger.info("generateVideo(), seedance, user: ${user.getId()}, retry: $retry, Error ${e.stackTraceToString()}")
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
            ResultApiAI.Error(e.message)
        }
    }

    override suspend fun getVideo(idVideo: String, user: User): ResultApiAI<Any, Any> {
        return try {
            logger.info("getVideo(), seedance, user: ${user.getId()}, start")

            val response = client.get("$API_VIDEOS_URL/$idVideo") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }.body<VideoTaskResultDto>()

            when (response.status) {
                "completed" -> {
                    val videoUrl = response.videoUrl
                        ?: response.resultUrl
                        ?: response.url
                        ?: throw RuntimeException("getVideo(), seedance, video URL not found for task ${response.id}")
                    val videoFile = client.get(videoUrl).body<File>()
                    logger.info("getVideo(), seedance, user: ${user.getId()}, video id: ${response.id}, getting video completed")
                    return ResultApiAI.Success(data = videoFile)
                }

                else -> {
                    val errorMsg = response.error?.message ?: "Unknown error"
                    logger.error("getVideo(), seedance, user: ${user.getId()}, video id: ${response.id}, getting video failed: $errorMsg")
                    return ResultApiAI.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            logger.info("getVideo(), seedance, user: ${user.getId()}, Error ${e.stackTraceToString()}")
            ResultApiAI.Error(e.message)
        }
    }

    override fun close() {
        client.close()
    }
}
