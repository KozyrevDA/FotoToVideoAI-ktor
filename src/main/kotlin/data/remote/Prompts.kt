package data.remote

import data.model.ChatMessage
import data.model.Settings
import kotlinx.coroutines.flow.first

object Prompts {
    val PROMPT_0 = """
        Оживи загруженную фотографию
    """.trimIndent()
    val PROMPT_1 = """
        Сделай плавный переход от фотографиии user-photo1 к фотографии user-photo2
    """.trimIndent()
    val PROMPT_2 = """
        Сделай видеоаватарку для блога
    """.trimIndent()
    val PROMPT_3 = ""
    val PROMPT_4 = """
        Сделай улыбающиегося человека с загруженной фотографии user-photo1
    """.trimIndent()
    val PROMPT_5 = """
        Сделай репотраж за столом с человеком с загруженной фотографии user-photo1
    """.trimIndent()
    val PROMPT_6 = """
        Сделай чтобы человек с загруженной фотографии user-photo1 махал рукой в кадре. Не меняй фон. 
        Не меняй человека.
    """.trimIndent()
    val PROMPT_7 = """
        Сделай чтобы человек с загруженной фотографии user-photo1 грозил пальцем в кадр
    """.trimIndent()

    suspend fun get(chatMessage: ChatMessage, settings: Settings): String {
        suspend fun promptFor(filter: String?): String = when (filter) {
            //ALL APP
            "animatePhoto" -> settings.prompt1.first()
            "beforeAfter" -> settings.prompt2.first()
            "videoAvatar" -> settings.prompt3.first()
            "videoFromPrompt" -> settings.prompt4.first()

            //OLD APP
            "templates/smiling.png", "smiling.png" -> settings.prompt5.first()
            "templates/reporting.png", "reporting.png" -> settings.prompt6.first()
            "templates/waves_his_hand.png", "waves_his_hand.png" -> settings.prompt7.first()
            "templates/shakes_finger.png", "shakes_finger.png" -> settings.prompt8.first()

            //NEW APP
            "templates_mp4/Girl_with_orange_hair.mp4", "Girl_with_orange_hair.mp4" -> settings.prompt13.first()
            "templates_mp4/Sitting_at_a_table.mp4", "Sitting_at_a_table.mp4" -> settings.prompt14.first()
            "templates_mp4/Smiling_guy.mp4", "Smiling_guy.mp4" -> settings.prompt15.first()
            "templates_mp4/Wagging_a_finger.mp4", "Wagging_a_finger.mp4" -> settings.prompt16.first()
            "templates_mp4/Waving_a_hand.mp4", "Waving_a_hand.mp4" -> settings.prompt17.first()
            else -> ""
        }

        val firstPrompt = promptFor(chatMessage.systemPromt.nameFilter)
        val secondTemplate = chatMessage.systemPromt.secondTemplatePath
        val secondPrompt = secondTemplate?.let { promptFor(it) }.orEmpty()
        val combinedPrompt = if (secondTemplate?.contains("templates") == true) {
            secondPrompt
        } else {
            firstPrompt + secondPrompt
        }

        val userInstruction = chatMessage.userPrompt?.let { " Используй доп. инструкцию: $it" }.orEmpty()

        return combinedPrompt + " язык для видео: ${chatMessage.language}" + userInstruction
    }
}