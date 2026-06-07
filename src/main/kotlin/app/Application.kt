package app

import data.model.*
import data.remote.ai.KlingHedra
import data.remote.ai.Seedance
import data.remote.ai.Veo3
import data.repository.ServerRepository
import data.shared.app.TypeApp
import extensions.getProperties
import extensions.getProperty
import extensions.watchFile
import features.billing.google.ConfirmationGoogle
import features.hashing.sha256.SHA256HashingService
import features.security.token.TokenConfig
import features.security.token.jwt.JwtTokenService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import plugins.*
import plugins.routing.configureRouting

private const val YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000
private const val FIVE_HOUR_MILLIS = 5 * 60L * 60 * 1000

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val logger = LoggerFactory.getLogger(Application::class.java)
    val serverRepository = ServerRepository(logger = logger)
    val settings = Settings.build(this)
    val remoteConfig = RemoteConfig.build(this)
    val pathConfFile = "anotherfile.conf"

    CoroutineScope(Dispatchers.IO).launch {
        watchFile(path = pathConfFile) {
            settings.update(path = pathConfFile)
            logger.info("watchFile(), settings, anotherfile.conf, update")
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        watchFile(path = pathConfFile) {
            remoteConfig.update(path = pathConfFile)
            logger.info("watchFile(), remoteConfig, anotherfile.conf, update")
        }
    }

    CoroutineScope(Dispatchers.IO).launch {
        while (isActive) {
            delay(12 * 60 * 60 * 1000L) // 12 часов
            serverRepository.images.cleanupTempImages()
        }
    }

    val databaseConfig = DatabaseConfig(
        url = getProperty("postgres.url"),
        user = getProperty("postgres.user"),
        password = getProperty("postgres.password")
    )
    val hashingService = SHA256HashingService()
    val tokenService = JwtTokenService()
    val tokenAccessConfig = TokenConfig(
        issuer = getProperty("jwt.issuer"),
        audience = getProperties("jwt.audience")?.get(0) ?: "",
        secret = getProperty("jwt.secret"),
        expiresIn = FIVE_HOUR_MILLIS
    )
    val tokenRefreshConfig = TokenConfig(
        issuer = getProperty("jwt.issuer"),
        audience = getProperties("jwt.audience")?.get(1) ?: "",
        secret = getProperty("jwt.secret"),
        expiresIn = YEAR_MILLIS
    )
    val applicationHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Constants.JSON)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 600_000
            socketTimeoutMillis = 600_000
        }
    }
    val googleAuthPhotoToVideoAI = ProviderAuth.GoogleProviderAuth(
        urlProvider = "${getProperty("google.urlProvider")}/users/callback",
        name = "google",
        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
        accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
        clientId = getProperty("google.clientId"),
        clientSecret = getProperty("google.clientSecret"),
        defaultScopes = listOf(
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email"
        ),
        extraAuthParameters = listOf("access_type" to "offline")
    )
    val authVkConfig = AuthVkConfig(
        accessTokenPhotoToVideoAI = getProperty("vk.access_token_phototovideoai"),
        version = getProperty("vk.version")
    )
    val seedanceApi = Seedance(
        serverRepository = serverRepository,
        apiKey = getProperty("laozhang.api_key"),
        settings = settings,
        logger = logger
    )
    val klingHedraApi = KlingHedra(
        serverRepository = serverRepository,
        apiKey = getProperty("hedra.api_key"),
        settings = settings,
        logger = logger
    )
    val veo3 = Veo3(
        serverRepository = serverRepository,
        apiKey = getProperty("laozhang.api_key"),
        apiKeyKie = getProperty("kie.api_key"),
        settings = settings,
        logger = logger,
        fallbackApi = klingHedraApi
    )
    val confirmationGoogle = ConfirmationGoogle(logger = logger)

    configureSerialization()
    configureAutoHeadResponse()
    configurePartialContent()
    configureDatabases(databaseConfig)
    configureAuthentication(
        config = tokenAccessConfig,
        httpClient = applicationHttpClient,
        googleAuthPhotoToVideoAI = googleAuthPhotoToVideoAI
    )
    configureRouting(
        confirmationGoogle = confirmationGoogle,
        serverRepository = serverRepository,
        hashingService = hashingService,
        tokenService = tokenService,
        tokenAccessConfig = tokenAccessConfig,
        tokenRefreshConfig = tokenRefreshConfig,
        authVkConfig = authVkConfig,
        schemes = mapOf(TypeApp.FOTO_TO_VIDEO_AI to getProperty("mobile-app-schemes.phototovideoai")),
        httpClient = applicationHttpClient,
        veo3Api = veo3,
        klingHedraApi = klingHedraApi,
        settings = settings,
        remoteConfig = remoteConfig,
        logger = logger
    )
}