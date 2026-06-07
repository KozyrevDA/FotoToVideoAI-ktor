package plugins.routing.test

import data.model.RemoteConfig
import data.model.Settings
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.first

fun Routing.test(
    settings: Settings,
    remoteConfig: RemoteConfig,
    logger: Logger
) {
    get("/test") {
        logger.info("get(\"/test\")")
        call.respond(HttpStatusCode.OK, "Test OK")
    }

    get("/conf") {
        call.respond(
            status = HttpStatusCode.OK,
            message = "Стартовые коины: ${settings.startCoins.first()},\n" +
                    "Коины за месячную подписку: ${settings.monthSubCoins.first()},\n" +
                    "Коины за годовую подписку: ${settings.yearSubCoins.first()},\n" +
                    "Коины за тестовый период годовой подписки за 1 рубль: ${settings.yearSubPromoStartCoins.first()},\n" +
                    "Коины за основной период годовой подписки за 1 рубль: ${settings.yearSubPromoCoins.first()},\n" +
                    "Стоимость месячной подписки VK Donut: ${remoteConfig.priceVkDonutRub.first()},\n" +
                    "Вкл/выкл онбординг: ${remoteConfig.showOnboarding.first()},\n" +
                    "Вкл/выкл пейвол при старте: ${remoteConfig.showStartPaywall.first()},\n" +
                    "Выбран китайский сервис: ${settings.chinaMainService.first()},\n" +
                    "Игнор проверки монет для IOS: ${settings.ignoreCoinsIos.first()}, \n" +
                    "Показывать кнопку \"Больше токенов\" без подписки: ${remoteConfig.showMoreTokensButtonWhenNonSub.first()}, \n" +
                    "Ипользовать Kie api: ${settings.kieApiMain.first()}"
        )
    }
}