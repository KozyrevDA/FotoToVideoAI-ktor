package plugins.routing

import data.model.AuthVkConfig
import data.model.RemoteConfig
import data.model.Settings
import data.remote.ApiAi
import data.repository.ServerRepository
import data.shared.app.TypeApp
import features.billing.google.ConfirmationGoogle
import features.hashing.HashingService
import features.security.token.TokenConfig
import features.security.token.TokenService
import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import plugins.routing.billing.billing
import plugins.routing.config.remoteConfig
import plugins.routing.images.images
import plugins.routing.templates.templates
import plugins.routing.test.test
import plugins.routing.users.users
import plugins.routing.videos.videos

fun Application.configureRouting(
    veo3Api: ApiAi,
    klingHedraApi: ApiAi,
    confirmationGoogle: ConfirmationGoogle,
    logger: Logger,
    serverRepository: ServerRepository,
    hashingService: HashingService,
    tokenService: TokenService,
    tokenAccessConfig: TokenConfig,
    tokenRefreshConfig: TokenConfig,
    authVkConfig: AuthVkConfig,
    schemes: Map<TypeApp, String>,
    httpClient: HttpClient,
    settings: Settings,
    remoteConfig: RemoteConfig,
) {
    routing {
        test(
            logger = logger,
            settings = settings,
            remoteConfig = remoteConfig
        )
        videos(
            veo3Api = veo3Api,
            klingHedraApi = klingHedraApi,
            serverRepository = serverRepository,
            tokenAccessConfig = tokenAccessConfig,
            tokenService = tokenService,
            settings = settings,
            logger = logger
        )
        images(
            serverRepository = serverRepository,
            logger = logger,
        )
        users(
            serverRepository = serverRepository,
            hashingService = hashingService,
            tokenService = tokenService,
            tokenAccessConfig = tokenAccessConfig,
            tokenRefreshConfig = tokenRefreshConfig,
            authVkConfig = authVkConfig,
            schemes = schemes,
            httpClient = httpClient,
            settings = settings,
            logger = logger
        )
        billing(
            confirmationGoogle = confirmationGoogle,
            serverRepository = serverRepository,
            settings = settings,
            logger = logger
        )
        templates(
            serverRepository = serverRepository,
            logger = logger
        )
        remoteConfig(
            remoteConfig = remoteConfig,
            settings = settings,
            logger = logger
        )
    }
}