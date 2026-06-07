package data.model

import com.typesafe.config.ConfigFactory
import extensions.getBooleanProperty
import extensions.getProperty
import io.ktor.server.application.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class Settings(
    val startCoins: MutableStateFlow<Int>,
    val monthSubCoins: MutableStateFlow<Int>,
    val yearSubCoins: MutableStateFlow<Int>,
    val yearSubPromoStartCoins: MutableStateFlow<Int>,
    val yearSubPromoCoins: MutableStateFlow<Int>,
    val chinaMainService: MutableStateFlow<Boolean?>,
    val ignoreCoinsIos: MutableStateFlow<Boolean?>,
    val prompt1: MutableStateFlow<String>,
    val prompt2: MutableStateFlow<String>,
    val prompt3: MutableStateFlow<String>,
    val prompt4: MutableStateFlow<String>,
    val prompt5: MutableStateFlow<String>,
    val prompt6: MutableStateFlow<String>,
    val prompt7: MutableStateFlow<String>,
    val prompt8: MutableStateFlow<String>,
    val prompt13: MutableStateFlow<String>,
    val prompt14: MutableStateFlow<String>,
    val prompt15: MutableStateFlow<String>,
    val prompt16: MutableStateFlow<String>,
    val prompt17: MutableStateFlow<String>,
    val kieApiMain: MutableStateFlow<Boolean?>,
    val isTrialEnabled: MutableStateFlow<Boolean?>,
) {
    companion object {
        fun build(application: Application) = with(application) {
            Settings(
                startCoins = MutableStateFlow(getProperty("settings.start_coins").toInt()),
                monthSubCoins = MutableStateFlow(getProperty("settings.month_sub_coins").toInt()),
                yearSubCoins = MutableStateFlow(getProperty("settings.year_sub_coins").toInt()),
                yearSubPromoStartCoins = MutableStateFlow(getProperty("settings.year_sub_promo_start_coins").toInt()),
                yearSubPromoCoins = MutableStateFlow(getProperty("settings.year_sub_promo_coins").toInt()),
                chinaMainService = MutableStateFlow(getBooleanProperty("settings.china_main_service")),
                ignoreCoinsIos = MutableStateFlow(getBooleanProperty("settings.ignore_coins_ios")),
                prompt1 = MutableStateFlow(getProperty("settings.prompt1")),
                prompt2 = MutableStateFlow(getProperty("settings.prompt2")),
                prompt3 = MutableStateFlow(getProperty("settings.prompt3")),
                prompt4 = MutableStateFlow(getProperty("settings.prompt4")),
                prompt5 = MutableStateFlow(getProperty("settings.prompt5")),
                prompt6 = MutableStateFlow(getProperty("settings.prompt6")),
                prompt7 = MutableStateFlow(getProperty("settings.prompt7")),
                prompt8 = MutableStateFlow(getProperty("settings.prompt8")),
                prompt13 = MutableStateFlow(getProperty("settings.prompt13")),
                prompt14 = MutableStateFlow(getProperty("settings.prompt14")),
                prompt15 = MutableStateFlow(getProperty("settings.prompt15")),
                prompt16 = MutableStateFlow(getProperty("settings.prompt16")),
                prompt17 = MutableStateFlow(getProperty("settings.prompt17")),
                kieApiMain = MutableStateFlow(getBooleanProperty("settings.kie_api_main")),
                isTrialEnabled = MutableStateFlow(getBooleanProperty("settings.is_trial_enabled")),
            )
        }
    }
}

fun Settings.update(path: String) {
    kotlin.runCatching {
        val conf = ConfigFactory.parseFile(File(path)).resolve()
        startCoins.value = conf.getString("settings.start_coins").toInt()
        monthSubCoins.value = conf.getString("settings.month_sub_coins").toInt()
        yearSubCoins.value = conf.getString("settings.year_sub_coins").toInt()
        yearSubPromoStartCoins.value = conf.getString("settings.year_sub_promo_start_coins").toInt()
        yearSubPromoCoins.value = conf.getString("settings.year_sub_promo_coins").toInt()
        chinaMainService.value = conf.getBoolean("settings.china_main_service")
        ignoreCoinsIos.value = conf.getBoolean("settings.ignore_coins_ios")
        prompt1.value = conf.getString("settings.prompt1")
        prompt2.value = conf.getString("settings.prompt2")
        prompt3.value = conf.getString("settings.prompt3")
        prompt4.value = conf.getString("settings.prompt4")
        prompt5.value = conf.getString("settings.prompt5")
        prompt6.value = conf.getString("settings.prompt6")
        prompt7.value = conf.getString("settings.prompt7")
        prompt8.value = conf.getString("settings.prompt8")

        prompt13.value = conf.getString("settings.prompt13")
        prompt14.value = conf.getString("settings.prompt14")
        prompt15.value = conf.getString("settings.prompt15")
        prompt16.value = conf.getString("settings.prompt16")
        prompt17.value = conf.getString("settings.prompt17")
        kieApiMain.value = conf.getBoolean("settings.kie_api_main")
        isTrialEnabled.value = conf.getBoolean("settings.is_trial_enabled")
    }
}