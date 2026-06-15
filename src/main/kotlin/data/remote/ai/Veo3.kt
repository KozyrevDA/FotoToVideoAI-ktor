package data.remote.ai

import app.Constants
import data.model.*
import data.remote.ApiAi
import data.remote.Prompts
import data.remote.ResultApiAI
import data.remote.ai.dto.*
import data.remote.handleApiAiError
import data.remote.isSafetyError
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
private const val MODEL_NAME_FAST = "veo-3.1-fast-generate-preview"

//TODO пока их нет в laozhang
//private const val MODEL_NAME_FAST = "veo-3.1-fast"
//private const val MODEL_NAME_LANDSCAPE_FAST = "veo-3.1-landscape-fast"
private const val WAIT_10_MINUTES = 10 * 60_000L

class Veo3(
    private val serverRepository: ServerRepository,
    private val settings: Settings,
    private val apiKey: String,
    private val apiKeyKie: String,
    private val logger: Logger,
    private val fallbackApi: ApiAi? = null,
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
        val veoResult = generateVideoVeo(
            chatMessage = chatMessage,
            photo1 = photo1,
            photo2 = photo2,
            user = user,
            queueGen = queueGen,
            kieAi = kieAi,
            retry = retry
        )

        if (veoResult is ResultApiAI.Success) return veoResult

        if (veoResult is ResultApiAI.Error && veoResult.error is String && isSafetyError(veoResult.error)) {
            return veoResult
        }

        if (fallbackApi == null) return veoResult

        logger.info("generateVideo(), veo3 failed, falling back to kling-hedra, user: ${user.getId()}")

        val fallbackQueueGen = queueGen.copy(status = QueueGenStatus.CREATED)
        return fallbackApi.generateVideo(
            chatMessage = chatMessage,
            photo1 = photo1,
            photo2 = photo2,
            user = user,
            queueGen = fallbackQueueGen,
            kieAi = false,
            retry = 0
        )
    }

    private suspend fun generateVideoVeo(
        chatMessage: ChatMessage,
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        queueGen: QueueGen,
        kieAi: Boolean,
        retry: Int
    ): ResultApiAI<Any, Any> {
        if (kieAi) {
            return generateVideoViaKie(
                chatMessage = chatMessage,
                photo1 = photo1,
                photo2 = photo2,
                user = user,
                queueGen = queueGen,
                retry = retry
            )
        }

        return try {
            val model = MODEL_NAME_FAST
            val aspectRatio = when (chatMessage.orientationType) {
                OrientationType.PORTRAIT -> "9:16"
                OrientationType.LANDSCAPE -> "16:9"
            }

            logger.info("generateVideo(), veo3, model: $model, user: ${user.getId()}, retry: $retry, start")

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

            val maxAttempts = 2
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

                    val isRetryableError = responseText.contains("负载已饱和") ||
                            responseText.contains("fail_to_fetch_task") ||
                            responseText.contains("upstream load")
                    if (isRetryableError && attempt < maxAttempts) {
                        logger.warn("generateVideo(), veo3, user: ${user.getId()}, attempt $attempt, server overloaded, retrying in 10s...")
                        delay(10_000L)
                        continue
                    }

                    break
                } catch (e: Exception) {
                    logger.warn("generateVideo(), veo3, user: ${user.getId()}, POST attempt $attempt failed: ${e.message}")
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
                logger.info("generateVideo(), veo3, model: $model, user: ${user.getId()}, Error bodyAsText: $responseText")
                serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                throw e
            }
            val startTime = System.currentTimeMillis()
            var latestPercent = -1

            while (true) {
                if (System.currentTimeMillis() - startTime > WAIT_10_MINUTES) {
                    serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.TIMEOUT))
                    throw RuntimeException("generateVideo(), veo3, model: $model, retry: $retry, video generation timeout")
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

                            logger.info("generateVideo(), veo3, model: $model, retry: $retry, user: ${user.getId()}, video id: ${videoTaskResultDto.id}, video generation completed")
                            return ResultApiAI.Success(data = finalVideoFile)
                        } finally {
                            if (tempVideoFile.exists()) {
                                tempVideoFile.delete()
                            }
                        }
                    }

                    "failed" -> {
                        val errorMsg = videoTaskResultDto.error?.message ?: "Unknown error"
                        logger.error("generateVideo(), veo3, model: $model, retry: $retry, user: ${user.getId()}, video id: ${videoTaskResultDto.id}, video generation failed: $errorMsg")

                        if (retry < 1 && (errorMsg.contains("\"code\":403") || errorMsg.contains("PUBLIC_ERROR_HIGH_TRAFFIC"))) {
                            return generateVideoVeo(
                                chatMessage = chatMessage,
                                photo1 = photo1,
                                photo2 = photo2,
                                user = user,
                                queueGen = queueGen,
                                kieAi = kieAi,
                                retry = retry + 1
                            )
                        }

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
                                "generateVideo(), veo3, model: $model, user: ${user.getId()}, retry: $retry, video not ready yet, " +
                                        "status=${videoTaskResultDto.status}, progress=$currentPercent%, waiting..."
                            )
                            latestPercent = currentPercent
                        }
                        delay(5000L)
                    }
                }
            }

            @Suppress("UNREACHABLE_CODE")
            ResultApiAI.Success("generateVideo(), veo3, model: $model, user: ${user.getId()}, video generated successfully")
        } catch (e: Exception) {
            logger.info("generateVideo(), veo3, user: ${user.getId()}, retry: $retry, Error ${e.stackTraceToString()}")
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
            ResultApiAI.Error(e.message)
        }
    }

    override suspend fun getVideo(idVideo: String, user: User): ResultApiAI<Any, Any> {
        return try {
            logger.info("getVideo(), veo3, user: ${user.getId()}, start")

            val response = client.get("$API_VIDEOS_URL/$idVideo") {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }.body<VideoTaskResultDto>()

            when (response.status) {
                "completed" -> {
                    val videoUrl = response.videoUrl
                        ?: response.resultUrl
                        ?: response.url
                        ?: throw RuntimeException("getVideo(), veo3, video URL not found for task ${response.id}")
                    val videoFile = retry(times = 3, delayMillis = 2000L) {
                        client.get(videoUrl).body<File>()
                    }
                    logger.info("getVideo(), veo3, user: ${user.getId()}, video id: ${response.id}, getting video completed")
                    return ResultApiAI.Success(data = videoFile)
                }

                else -> {
                    val errorMsg = response.error?.message ?: "Unknown error"
                    logger.error("getVideo(), veo3, user: ${user.getId()}, video id: ${response.id}, getting video failed: $errorMsg")
                    return ResultApiAI.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            logger.info("getVideo(), veo3, user: ${user.getId()}, Error ${e.stackTraceToString()}")
            ResultApiAI.Error(e.message)
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun <T> retry(times: Int, delayMillis: Long = 0L, block: suspend () -> T): T {
        var lastError: Throwable? = null
        repeat(times) {
            try {
                return block()
            } catch (e: Throwable) {
                lastError = e
                delay(delayMillis)
            }
        }
        throw lastError ?: RuntimeException("retry(), veo3, Unknown error in retry")
    }

    private suspend fun generateVideoViaKie(
        chatMessage: ChatMessage,
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        queueGen: QueueGen,
        retry: Int
    ): ResultApiAI<Any, Any> {
        logger.info("generateVideoViaKie(), veo3, user: ${user.getId()}, retry: $retry, start")

        serverRepository.images.saveDoubleOriginImages(
            photo1 = photo1,
            photo2 = photo2,
            user = user
        )

        val image1Url = photo1?.let {
            serverRepository.images.createTempImage(imgBytes = it)
        }
        val image2Url = photo2?.let {
            serverRepository.images.createTempImage(imgBytes = it)
        }
        val imageUrls = buildList {
            image1Url?.let { add(it) }
            image2Url?.let { add(it) }
        }

        serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.GENERATION))

        val aspectRatio = when (chatMessage.orientationType) {
            OrientationType.PORTRAIT -> "9:16"
            OrientationType.LANDSCAPE -> "16:9"
        }

        val payload = VeoGenerateRequest(
            prompt = Prompts.get(chatMessage, settings),
            imageUrls = imageUrls,
            model = "veo3_fast",
            aspectRatio = aspectRatio,
            enableFallback = false,
            enableTranslation = true
        )

        val maxAttempts = 12
        var attempt = 0
        var createTaskResponseText = ""

        while (true) {
            try {
                attempt++
                val response = client.post("https://api.kie.ai/api/v1/veo/generate") {
                    header(HttpHeaders.Authorization, "Bearer $apiKeyKie")
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
                createTaskResponseText = response.bodyAsText()
                break
            } catch (e: Exception) {
                logger.warn(
                    "generateVideoViaKie(), veo3, user: ${user.getId()}, " +
                            "createTask attempt $attempt failed: ${e.message}"
                )

                if (attempt >= maxAttempts) {
                    serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                    return ResultApiAI.Error("createTask failed after $attempt attempts")
                }
                delay(5_000)
            }
        }

        val responseObj = try {
            Constants.JSON.decodeFromString<VeoGenerateResponse>(createTaskResponseText)
        } catch (e: Exception) {
            logger.info("generateVideoViaKie(), veo3, user: ${user.getId()}, Error parse: $createTaskResponseText, exception: ${e.message}")
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
            return ResultApiAI.Error("Error parse response")
        }

        val taskId = responseObj.data?.taskId ?: run {
            serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
            return ResultApiAI.Error("Error, taskId null, message: ${responseObj.msg}")
        }

        val startTime = System.currentTimeMillis()
        var latestPercent = -1

        while (true) {
            if (System.currentTimeMillis() - startTime > WAIT_10_MINUTES) {
                serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.TIMEOUT))
                return ResultApiAI.Error("video generation timeout")
            }

            val taskResultObj = try {
                val taskResultResponseText = client.get("https://api.kie.ai/api/v1/veo/record-info") {
                    header(HttpHeaders.Authorization, "Bearer $apiKeyKie")
                    url { parameters.append("taskId", taskId) }
                }.bodyAsText()

                Constants.JSON.decodeFromString<VeoTaskResponse>(taskResultResponseText)
            } catch (_: Exception) {
                VeoTaskResponse(code = 0, msg = "", data = VeoTaskDataResult(taskId = taskId, successFlag = 0))
            }

            when (taskResultObj.data?.successFlag) {
                1 -> {
                    val videoUrl = taskResultObj.data.response?.resultUrls?.firstOrNull() ?: run {
                        serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                        return ResultApiAI.Error("Video URL not found from API")
                    }

                    val tempDir = File("raw/tmp")
                    tempDir.mkdirs()

                    val tempVideoFile = File(tempDir, "$taskId.mp4")
                    val channel = client.get(videoUrl).bodyAsChannel()
                    tempVideoFile.outputStream().use { out ->
                        val buffer = ByteArray(8 * 1024)
                        while (!channel.isClosedForRead) {
                            val bytesRead = channel.readAvailable(buffer)
                            if (bytesRead > 0) out.write(buffer, 0, bytesRead)
                        }
                    }

                    try {
                        serverRepository.videos.saveGeneratedVideo(
                            idVideo = taskId,
                            orientationType = chatMessage.orientationType,
                            user = user
                        )
                        val finalVideoFile = serverRepository.videosFiles.saveGeneratedVideo(
                            videoFile = tempVideoFile,
                            videoId = taskId,
                            user = user
                        )
                        serverRepository.images.generateThumbnail(
                            videoFile = tempVideoFile,
                            idVideo = taskId,
                            user = user
                        )
                        serverRepository.queueGen.upsert(
                            queueGen.copy(
                                idVideo = taskId,
                                status = QueueGenStatus.COMPLETED
                            )
                        )

                        logger.info("generateVideoViaKie(), veo3, user: ${user.getId()}, video id: $taskId, video generation completed")
                        return ResultApiAI.Success(data = finalVideoFile)
                    } finally {
                        if (tempVideoFile.exists()) {
                            tempVideoFile.delete()
                        }
                    }
                }

                2, 3 -> {
                    val failMsg = taskResultObj.data.response?.failMsg
                    serverRepository.queueGen.upsert(queueGen.copy(status = QueueGenStatus.FAILED))
                    return ResultApiAI.Error(failMsg ?: "Kie video generation failed")
                }

                else -> {
                    val progress = taskResultObj.data?.progress ?: latestPercent
                    if (latestPercent < progress) {
                        latestPercent = progress
                        logger.info("generateVideoViaKie(), user: ${user.getId()}, progress: $progress%")
                    }
                    delay(5000L)
                }
            }
        }
    }
}