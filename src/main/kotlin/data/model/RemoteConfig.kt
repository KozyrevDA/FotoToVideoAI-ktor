package data.model

import com.typesafe.config.ConfigFactory
import extensions.getBooleanProperty
import extensions.getProperty
import io.ktor.server.application.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class RemoteConfig(
    val priceVkDonutRub: MutableStateFlow<Int>,
    val showOnboarding: MutableStateFlow<Boolean?>,
    val showStartPaywall: MutableStateFlow<Boolean?>,
    val showMoreTokensButtonWhenNonSub: MutableStateFlow<Boolean?>,
    val startCoins: MutableStateFlow<Int>,
    val showTrialGeneration: MutableStateFlow<Boolean?>,
) {
    companion object {
        fun build(application: Application) = with(application) {
            RemoteConfig(
                priceVkDonutRub = MutableStateFlow(getProperty("remote-config.price_vk_donut_rub").toInt()),
                showOnboarding = MutableStateFlow(getBooleanProperty("remote-config.show_onboarding")),
                showStartPaywall = MutableStateFlow(getBooleanProperty("remote-config.show_start_paywall")),
                showMoreTokensButtonWhenNonSub = MutableStateFlow(getBooleanProperty("remote-config.show_more_tokens_button_when_non_sub")),
                startCoins = MutableStateFlow(getProperty("settings.start_coins").toInt()),
                showTrialGeneration = MutableStateFlow(getBooleanProperty("remote-config.show_trial_generation"))
            )
        }
    }
}

fun RemoteConfig.update(path: String) {
    kotlin.runCatching {
        val conf = ConfigFactory.parseFile(File(path)).resolve()
        priceVkDonutRub.value = conf.getString("remote-config.price_vk_donut_rub").toInt()
        showOnboarding.value = conf.getBoolean("remote-config.show_onboarding")
        showStartPaywall.value = conf.getBoolean("remote-config.show_start_paywall")
        showMoreTokensButtonWhenNonSub.value = conf.getBoolean("remote-config.show_more_tokens_button_when_non_sub")
        startCoins.value = conf.getString("settings.start_coins").toInt()
        showTrialGeneration.value = conf.getBoolean("remote-config.show_trial_generation")
    }
}