package plugins.routing.videos

import app.Constants
import data.dto.ChatMessageDto
import data.dto.toModel
import data.model.*
import data.remote.ApiAi
import data.remote.ResultApiAI
import data.repository.ServerRepository
import data.repository.postgres.TrialUsageDAO
import data.repository.postgres.TrialUsageTable
import data.repository.postgres.suspendTransaction
import extensions.getClaim
import features.security.token.TokenConfig
import features.security.token.TokenService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

const val PRICE_VIDEO_GENERATION = 300

fun Routing.videos(
    veo3Api: ApiAi,
    klingHedraApi: ApiAi,
    serverRepository: ServerRepository,
    tokenAccessConfig: TokenConfig,
    tokenService: TokenService,
    settings: Settings,
    logger: Logger
) {
    trialGeneration(
        klingHedraApi = klingHedraApi,
        serverRepository = serverRepository,
        settings = settings,
        logger = logger
    )

    get("/videos/trial/{id_video}") {
        try {
            val idVideo = call.parameters["id_video"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty idVideo field")
                return@get
            }
            val videoFile = serverRepository.videosFiles.findVideoById(idVideo = idVideo) ?: run {
                call.respond(HttpStatusCode.NotFound, "Video not found")
                return@get
            }
            call.respondFile(videoFile)
        } catch (_: ClosedWriteChannelException) {
        } catch (_: ClosedByteChannelException) {
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    authenticate {
        post("/generate/video") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("post(\"/generate/videos\"), Empty authenticate field")
                    return@post
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User not found")
                    logger.info("post(\"/generate/videos\"), user: $emailRegistration not found")
                    return@post
                }
                val generationCount = serverRepository.queueGen.getAll(
                    status = QueueGenStatus.GENERATION.name,
                    user = user
                ).size + 1

                val resultCount = user.coins - (PRICE_VIDEO_GENERATION * generationCount)

                if (resultCount < 0) {
                    call.respond(HttpStatusCode.PaymentRequired, "Not enough coins")
                    logger.info("post(\"/generate/videos\"), user: ${user.getId()}, Not enough coins")
                    return@post
                }

                val multipart = call.receiveMultipart()
                var photo1: ByteArray? = null
                var photo2: ByteArray? = null
                var metadataJson: String? = null

                try {
                    withTimeout(120_000) {
                        withContext(Dispatchers.IO) {
                            multipart.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        if (part.name == "metadata") metadataJson = part.value
                                    }

                                    is PartData.FileItem -> {
                                        if (part.name == "photo1") {
                                            photo1 = part.streamProvider().readBytes()
                                        } else if (part.name == "photo2") {
                                            photo2 = part.streamProvider().readBytes()
                                        }
                                    }

                                    else -> {}
                                }
                                part.dispose()
                            }
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Uploaded multipart error")
                    logger.info("post(\"/generate/videos\"), user: ${user.getId()}, timeout upload multipart")
                    return@post
                }

                val chatMessage = metadataJson
                    ?.let { Constants.JSON.decodeFromString<ChatMessageDto>(it) }
                    ?.toModel() ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Uploaded chatMessage error")
                    logger.info("post(\"/generate/videos\"), user: ${user.getId()}, chatMessage uploaded error")
                    return@post
                }

                logger.info("post(\"/generate/videos\"), user: ${user.getId()}, userPrompt: ${chatMessage.userPrompt}, systemPromt: ${chatMessage.systemPromt}")

                val queueGen = QueueGen(
                    uid = UUID.randomUUID().toString(),
                    emailReg = user.emailReg,
                    orientationType = chatMessage.orientationType,
                    apiAiType = chatMessage.apiAi,
                    createdAt = LocalDateTime.now(),
                    status = QueueGenStatus.CREATED,
                    userPrompt = chatMessage.userPrompt
                )
                val kieAi = settings.kieApiMain.value == true

                serverRepository.queueGen.upsert(queueGen)

                when (
                    val resultApiAI = veo3Api.generateVideo(
                        chatMessage = chatMessage,
                        photo1 = photo1,
                        photo2 = photo2,
                        user = user,
                        kieAi = kieAi,
                        queueGen = queueGen
                    )
                ) {
                    is ResultApiAI.Success -> {
                        serverRepository.user.updateCoins(
                            user = user,
                            resultCount = serverRepository.user.getActualCoinsCount(user) - PRICE_VIDEO_GENERATION
                        )

                        call.respond(HttpStatusCode.OK)
                        logger.info("post(\"/generate/videos\"), user: ${user.getId()}, ResultApiAI.Success")
                    }

                    ResultApiAI.Empty -> {
                        call.respond(HttpStatusCode.NoContent)
                        logger.info("post(\"/generate/videos\"), user: ${user.getId()}, ResultApiAI.Empty")
                    }

                    is ResultApiAI.Error -> {
                        if (resultApiAI.error.toString() == "当前不支持上传包含写实人物的图像。" ||
                            resultApiAI.error.toString() == "此内容可能违反关于裸露、性内容或色情内容的相关规定。" ||
                            resultApiAI.error.toString() == "输入的提示词或视频的输出内容违反了OpenAI的相关服务政策，请调整提示词后进行重试" ||
                            resultApiAI.error.toString() == "图片中包含未成年人，请重新上传" ||
                            resultApiAI.error.toString() == "此内容可能违反关于第三方内容相似性的相关规定。" ||
                            resultApiAI.error.toString() == "此内容可能违反了我们的内容政策。" ||
                            resultApiAI.error.toString() == "PUBLIC_ERROR_PROMINENT_PEOPLE_FILTER_FAILED" ||
                            resultApiAI.error.toString() == "PUBLIC_ERROR_SEXUAL"
                        ) {
                            call.respond(HttpStatusCode.InternalServerError, "ErrorSafetySystem")
                        } else {
                            call.respond(HttpStatusCode.InternalServerError, resultApiAI.error.toString())
                        }
                        logger.info("post(\"/generate/videos\"), user: ${user.getId()}, ResultApiAI.Error ${resultApiAI.error}")
                    }

                    ResultApiAI.ErrorSafetySystem -> {
                        call.respond(HttpStatusCode.InternalServerError, "ErrorSafetySystem")
                        logger.info("post(\"/generate/videos\"), user: ${user.getId()}, ResultApiAI.ErrorSafetySystem")
                    }
                }
            } catch (c: ClosedWriteChannelException) {
            } catch (c: ClosedByteChannelException) {
            } catch (e: Exception) {
                logger.info("post(\"/generate/videos\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/videos/all") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("post(\"/videos/all\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User not found")
                    logger.info("post(\"/videos/all\"), user: $emailRegistration not found")
                    return@get
                }
                val videos = serverRepository.videos.getAllInfoVideos(user)
                    .sortedByDescending { it.createdAt }
                    .map { it.toDto() }

                call.respond(HttpStatusCode.OK, videos)
            } catch (e: Exception) {
                logger.info("post(\"/videos/all\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/videos/thumbnail/{id_video}") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("post(\"/videos/thumbnail\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User not found")
                    logger.info("post(\"/videos/thumbnail\"), user: $emailRegistration not found")
                    return@get
                }
                val idVideo = call.parameters["id_video"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty idVideo field")
                    logger.info("post(\"/videos/thumbnail\"), user: $emailRegistration, Empty idVideo field")
                    return@get
                }
                val image = serverRepository.images.getAllImages(user)
                    .find { it.nameWithoutExtension == "thumb_$idVideo" }

                if (image != null && image.exists()) {
                    call.respondFile(image)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        "Thumbnail for video id $idVideo not found"
                    )
                }
            } catch (e: Exception) {
                logger.info("post(\"/videos/thumbnail\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/videos/get/{id_video}") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("post(\"/videos/get\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User not found")
                    logger.info("post(\"/videos/get\"), user: $emailRegistration not found")
                    return@get
                }
                val idVideo = call.parameters["id_video"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty idVideo field")
                    logger.info("post(\"/videos/get\"), user: $emailRegistration, Empty idVideo field")
                    return@get
                }

                serverRepository.videos.getInfoVideo(idVideo = idVideo, user = user) ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Video access denied")
                    logger.info("post(\"/videos/get\"), user: $emailRegistration, idVideo: $idVideo, Video access denied")
                    return@get
                }

                val videoFile = serverRepository.videosFiles.getVideo(idVideo = idVideo, user = user) ?: run {
                    logger.info("post(\"/generate/get\"), user: ${user.getId()}, idVideo: $idVideo, video file not found")
                    call.respond(HttpStatusCode.InternalServerError, "video file not found")
                    return@get
                }

                call.respondFile(videoFile)

                logger.info("post(\"/generate/get\"), user: ${user.getId()}, ResultApiAI.Success")
            } catch (_: ClosedWriteChannelException) {
            } catch (_: ClosedByteChannelException) {
            } catch (e: Exception) {
                logger.info("post(\"/videos/get\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/generate/is-processing") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("get(\"/image/is-generation\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                    logger.info("get(\"/image/is-generation\"), User not found")
                    return@get
                }
                val isProcessing = user.isProcessing

                call.respond(
                    status = HttpStatusCode.OK,
                    message = isProcessing
                )
            } catch (e: Exception) {
                logger.info("post(\"/generate/is-processing\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        post("/image/origin/upload") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("get(\"/image/origin/upload\"), Empty authenticate field")
                    return@post
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User not found")
                    logger.info("get(\"/image/origin/upload\"), emailReg: $emailRegistration, User not found")
                    return@post
                }
                val multipart = call.receiveMultipart()
                var photo1ImageBytes: ByteArray? = null
                var photo2ImageBytes: ByteArray? = null
                var metadataJson: String? = null

                try {
                    withTimeout(120_000) {
                        withContext(Dispatchers.IO) {
                            multipart.forEachPart { part ->
                                when (part) {
                                    is PartData.FileItem -> {
                                        when (part.name) {
                                            "photo1" -> photo1ImageBytes = part.streamProvider().readBytes()
                                            "photo2" -> photo2ImageBytes = part.streamProvider().readBytes()
                                        }
                                    }

                                    is PartData.FormItem -> {
                                        if (part.name == "metadata") metadataJson = part.value
                                    }

                                    else -> {}
                                }
                                part.dispose()
                            }
                        }
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, "Uploaded multipart error")
                    logger.info("post(\"/image/origin/upload\"), user: ${user.getId()}, timeout upload multipart")
                    return@post
                }

                val chatMessage = metadataJson
                    ?.let { Constants.JSON.decodeFromString<ChatMessageDto>(it) }
                    ?.toModel() ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Uploaded chatMessage error")
                    logger.info("post(\"/image/origin/upload\"), user: ${user.getId()}, chatMessage uploaded error")
                    return@post
                }
                val queueGen = QueueGen(
                    uid = UUID.randomUUID().toString(),
                    emailReg = user.emailReg,
                    orientationType = chatMessage.orientationType,
                    apiAiType = chatMessage.apiAi,
                    createdAt = LocalDateTime.now(),
                    status = QueueGenStatus.NOT_ENOUGH_COINS,
                    userPrompt = chatMessage.userPrompt
                )

                serverRepository.queueGen.upsert(queueGen)

                serverRepository.images.saveDoubleOriginImages(
                    photo1 = photo1ImageBytes,
                    photo2 = photo2ImageBytes,
                    user = user,
                    notEnoughCoins = true
                )

                call.respond(HttpStatusCode.OK)
            } catch (e: Exception) {
                logger.info("post(\"/image/origin/upload\"), Exception: ${e.stackTraceToString()}")
            }
        }

        get("/generate/queue") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("get(\"/generate/queue\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                    logger.info("get(\"/generate/queue\"), User not found")
                    return@get
                }
                val status = call.parameters["status"] ?: run {
                    call.respond(HttpStatusCode.Conflict, "Empty status field")
                    logger.info("get(\"/generate/queue\"), Empty status field")
                    return@get
                }
                val queueGenList = serverRepository.queueGen.getAll(
                    status = status,
                    user = user
                )
                val now = LocalDateTime.now()

                queueGenList.forEach { queue ->
                    if (queue.status == QueueGenStatus.GENERATION) {
                        val elapsedMinutes = Duration.between(queue.createdAt, now).toMinutes()
                        if (elapsedMinutes > 15) {
                            serverRepository.queueGen.upsert(queue.copy(status = QueueGenStatus.TIMEOUT))
                            logger.info("get(\"/generate/queue\"), Queue ${queue.uid} marked as TIMEOUT due to timeout")
                        }
                    }
                }

                call.respond(
                    status = HttpStatusCode.OK,
                    message = queueGenList
                        .sortedByDescending { it.createdAt }
                        .map { it.toDto() }
                )
            } catch (e: Exception) {
                logger.info("post(\"/generate/queue\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/generate/status/video") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("get(\"/generate/status/video\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                    logger.info("get(\"/generate/status/video\"), User not found")
                    return@get
                }
                val uid = call.parameters["uid"] ?: run {
                    call.respond(HttpStatusCode.Conflict, "Empty status field")
                    logger.info("get(\"/generate/status/video\"), Empty status field")
                    return@get
                }
                val queueGen = serverRepository.queueGen.getByUid(
                    uid = uid,
                    user = user
                )

                if (queueGen == null) {
                    call.respond(HttpStatusCode.InternalServerError, "queue gen null")
                } else {
                    call.respond(
                        status = HttpStatusCode.OK,
                        message = queueGen.toDto()
                    )
                }
            } catch (e: Exception) {
                logger.info("get(\"/generate/status/video\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }

        get("/videos/delete") {
            try {
                val emailRegistration = call.getClaim("email_registration") ?: run {
                    call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                    logger.info("get(\"/videos/delete\"), Empty authenticate field")
                    return@get
                }
                val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                    call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                    logger.info("get(\"/videos/delete\"), User not found")
                    return@get
                }
                val idVideo = call.parameters["id_video"] ?: run {
                    call.respond(HttpStatusCode.Conflict, "Empty idVideo field")
                    logger.info("get(\"/videos/delete\"), Empty idVideo field")
                    return@get
                }

                serverRepository.videos.deleteVideo(idVideo = idVideo, user = user)
            } catch (e: Exception) {
                logger.info("get(\"/videos/delete\"), Exception: ${e.stackTraceToString()}")
                call.respond(HttpStatusCode.InternalServerError, "Internal server error")
            }
        }
    }

    get("/videos/get/ios/{id_video}") {
        try {
            val accessToken = call.request.queryParameters["accessToken"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                logger.info("post(\"/videos/get/ios\"), Empty authenticate field")
                return@get
            }
            val decodedJWT = tokenService.getDecodedJwt(tokenAccessConfig, accessToken) ?: run {
                call.respond(HttpStatusCode.Unauthorized, "DecodedJWT is null")
                logger.info("post(\"/videos/get/ios\"), DecodedJWT is null")
                return@get
            }
            val emailRegistration = decodedJWT.getClaim("email_registration")?.asString() ?: run {
                call.respond(HttpStatusCode.BadRequest, "Missing email_registration")
                logger.info("post(\"/videos/get/ios\"), Missing claim email_registration")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User not found")
                logger.info("post(\"/videos/get/ios\"), User not found")
                return@get
            }
            val idVideo = call.parameters["id_video"] ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty idVideo field")
                logger.info("post(\"/videos/get/ios\"), user: $emailRegistration, Empty idVideo field")
                return@get
            }

            serverRepository.videos.getInfoVideo(idVideo = idVideo, user = user) ?: run {
                call.respond(HttpStatusCode.BadRequest, "Video access denied")
                logger.info("post(\"/videos/get/ios\"), user: $emailRegistration, idVideo: $idVideo, Video access denied")
                return@get
            }

            val videoFile = serverRepository.videosFiles.getVideo(idVideo = idVideo, user = user) ?: run {
                logger.info("post(\"/generate/get/ios\"), user: ${user.getId()}, idVideo: $idVideo, video file not found")
                call.respond(HttpStatusCode.InternalServerError, "video file not found")
                return@get
            }

            call.respondFile(videoFile)

            logger.info("post(\"/generate/get/ios\"), user: ${user.getId()}, ResultApiAI.Success")
        } catch (_: ClosedWriteChannelException) {
        } catch (_: ClosedByteChannelException) {
        } catch (e: Exception) {
            logger.info("post(\"/videos/get/ios\"), Exception: ${e.stackTraceToString()}")
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }
}

private fun Routing.trialGeneration(
    klingHedraApi: ApiAi,
    serverRepository: ServerRepository,
    settings: Settings,
    logger: Logger
) {
    post("/generate/trial") {
        try {
            if (settings.isTrialEnabled.value != true) {
                call.respond(HttpStatusCode.Forbidden, "Trial mode is disabled")
                logger.info("post(\"/generate/trial\"), trial mode is disabled via settings")
                return@post
            }

            val multipart = call.receiveMultipart()
            var photo1: ByteArray? = null
            var deviceUid: String? = null

            try {
                withTimeout(120_000) {
                    withContext(Dispatchers.IO) {
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    if (part.name == "device_uid") deviceUid = part.value
                                }

                                is PartData.FileItem -> {
                                    if (part.name == "photo1") {
                                        photo1 = part.streamProvider().readBytes()
                                    }
                                }

                                else -> {}
                            }
                            part.dispose()
                        }
                    }
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Uploaded multipart error")
                logger.info("post(\"/generate/trial\"), timeout upload multipart")
                return@post
            }

            if (deviceUid.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Missing device_uid")
                return@post
            }

            if (photo1 == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing photo1")
                return@post
            }

            val alreadyUsed = suspendTransaction {
                TrialUsageDAO.find { TrialUsageTable.deviceUid eq deviceUid!! }.firstOrNull() != null
            }

            if (alreadyUsed) {
                call.respond(HttpStatusCode.Conflict, "Trial already used")
                logger.info("post(\"/generate/trial\"), deviceUid: $deviceUid, trial already used")
                return@post
            }

            suspendTransaction {
                TrialUsageDAO.new {
                    this.deviceUid = deviceUid!!
                }
            }

            val trialEmailReg = "trial_${deviceUid}"
            val trialUser = User(
                emailReg = trialEmailReg,
                typeApp = data.shared.app.TypeApp.FOTO_TO_VIDEO_AI,
                coins = 0
            )

            val chatMessage = ChatMessage(
                userPrompt = null,
                systemPromt = SystemPromt(nameFilter = "animatePhoto", secondTemplatePath = null),
                isUser = true,
                orientationType = OrientationType.PORTRAIT,
                language = "ru",
                apiAi = ApiAiType.KLING,
                timestamp = kotlinx.datetime.Clock.System.now()
            )

            val queueGen = QueueGen(
                uid = UUID.randomUUID().toString(),
                emailReg = trialEmailReg,
                orientationType = OrientationType.PORTRAIT,
                apiAiType = ApiAiType.KLING,
                createdAt = LocalDateTime.now(),
                status = QueueGenStatus.CREATED
            )

            serverRepository.queueGen.upsert(queueGen)

            when (
                val result = klingHedraApi.generateVideo(
                    chatMessage = chatMessage,
                    photo1 = photo1,
                    photo2 = null,
                    user = trialUser,
                    queueGen = queueGen,
                    kieAi = false
                )
            ) {
                is ResultApiAI.Success -> {
                    call.respondText(queueGen.uid, status = HttpStatusCode.OK)
                    logger.info("post(\"/generate/trial\"), deviceUid: $deviceUid, ResultApiAI.Success")
                }

                is ResultApiAI.Error -> {
                    call.respond(HttpStatusCode.InternalServerError, result.error?.toString() ?: "Generation failed")
                    logger.info("post(\"/generate/trial\"), deviceUid: $deviceUid, ResultApiAI.Error: ${result.error}")
                }

                ResultApiAI.ErrorSafetySystem -> {
                    call.respond(HttpStatusCode.InternalServerError, "ErrorSafetySystem")
                    logger.info("post(\"/generate/trial\"), deviceUid: $deviceUid, ResultApiAI.ErrorSafetySystem")
                }

                ResultApiAI.Empty -> {
                    call.respond(HttpStatusCode.NoContent)
                    logger.info("post(\"/generate/trial\"), deviceUid: $deviceUid, ResultApiAI.Empty")
                }
            }
        } catch (_: ClosedWriteChannelException) {
        } catch (_: ClosedByteChannelException) {
        } catch (e: Exception) {
            logger.info("post(\"/generate/trial\"), Exception: ${e.stackTraceToString()}")
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }
}