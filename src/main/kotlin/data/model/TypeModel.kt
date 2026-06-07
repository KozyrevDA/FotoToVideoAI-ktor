package data.model

enum class TypeModel(
    val confString: String,
    val modelName: String
) {
    GPT_IMAGE_1(
        confString = "gpt",
        modelName = "gpt-image-1"
    ),
    GEMINI_2_5_FLASH_IMAGE_PREVIEW(
        confString = "gemini",
        modelName = "gemini-2.5-flash-image-preview"
    );

    companion object {
        fun fromConfString(value: String): TypeModel {
            return entries.find { it.confString == value } ?: GPT_IMAGE_1
        }
    }
}