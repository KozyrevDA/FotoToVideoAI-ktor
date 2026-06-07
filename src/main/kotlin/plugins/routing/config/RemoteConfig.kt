package plugins.routing.config

import data.model.RemoteConfig
import data.model.Settings
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.first

fun Routing.remoteConfig(
    remoteConfig: RemoteConfig,
    settings: Settings,
    logger: Logger
) {
    get("prices/vk-donut") {
        call.respond(HttpStatusCode.OK, remoteConfig.priceVkDonutRub.first())
    }

    get("v2/remote-config/prices/vk-donut") {
        call.respond(HttpStatusCode.OK, remoteConfig.priceVkDonutRub.first())
    }

    get("v2/remote-config/show-onboarding") {
        call.respond(HttpStatusCode.OK, remoteConfig.showOnboarding.first() ?: true)
    }

    get("v2/remote-config/show-start-paywall") {
        call.respond(HttpStatusCode.OK, remoteConfig.showStartPaywall.first() ?: true)
    }

    get("v2/remote-config/month-sub-coins") {
        call.respond(HttpStatusCode.OK, settings.monthSubCoins.first())
    }

    get("v2/remote-config/show-more-tokens-button-when-non-sub") {
        call.respond(HttpStatusCode.OK, remoteConfig.showMoreTokensButtonWhenNonSub.first() ?: true)
    }

    get("v2/remote-config/start-coins") {
        call.respond(HttpStatusCode.OK, remoteConfig.startCoins.first())
    }

    get("v2/remote-config/show-trial-generation") {
        call.respond(HttpStatusCode.OK, remoteConfig.showTrialGeneration.first() ?: false)
    }
}