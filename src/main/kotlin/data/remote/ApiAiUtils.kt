package data.remote

import data.model.QueueGen
import data.model.QueueGenStatus

private val SAFETY_ERROR_MESSAGES = setOf(
    "当前不支持上传包含写实人物的图像。",
    "此内容可能违反关于裸露、性内容或色情内容的相关规定。",
    "PUBLIC_ERROR_SEXUAL",
    "输入的提示词或视频的输出内容违反了OpenAI的相关服务政策，请调整提示词后进行重试",
    "此内容可能违反了我们的内容政策。",
    "图片中包含未成年人，请重新上传",
    "图片中包含名人，请重新上传",
    "PUBLIC_ERROR_PROMINENT_PEOPLE_FILTER_FAILED"
)

fun isSafetyError(errorMsg: String): Boolean {
    return errorMsg in SAFETY_ERROR_MESSAGES
}

fun handleApiAiError(
    errorMsg: String,
    queueGen: QueueGen,
    idVideo: String
): QueueGen {
    return when (errorMsg) {
        "当前不支持上传包含写实人物的图像。" -> queueGen.copy(
            idVideo = idVideo,
            status = QueueGenStatus.ERROR_SAFETY_SYSTEM_HUMAN
        )

        "此内容可能违反关于裸露、性内容或色情内容的相关规定。", "PUBLIC_ERROR_SEXUAL" -> queueGen.copy(
            idVideo = idVideo,
            status = QueueGenStatus.ERROR_SAFETY_SYSTEM_SEX_CONTENT
        )

        "输入的提示词或视频的输出内容违反了OpenAI的相关服务政策，请调整提示词后进行重试",
        "此内容可能违反了我们的内容政策。" ->
            queueGen.copy(
                idVideo = idVideo,
                status = QueueGenStatus.TERMS_OF_SERVICE
            )

        "图片中包含未成年人，请重新上传" ->
            queueGen.copy(
                idVideo = idVideo,
                status = QueueGenStatus.MINOR_CHILDREN
            )

        "图片中包含名人，请重新上传", "PUBLIC_ERROR_PROMINENT_PEOPLE_FILTER_FAILED" ->
            queueGen.copy(
                idVideo = idVideo,
                status = QueueGenStatus.CELEBRITIES
            )

        else -> queueGen.copy(
            idVideo = idVideo,
            status = QueueGenStatus.FAILED
        )
    }
}