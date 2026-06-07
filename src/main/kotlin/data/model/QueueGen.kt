package data.model

import data.dto.QueueGenDto
import java.time.LocalDateTime

data class QueueGen(
    val uid: String,
    val emailReg: String,
    val orientationType: OrientationType,
    val apiAiType: ApiAiType,
    val createdAt: LocalDateTime,
    val status: QueueGenStatus,
    val idVideo: String? = null,
    val userPrompt: String? = null,
)

fun QueueGen.toDto() = QueueGenDto(
    uid = uid,
    orientationType = orientationType.name,
    apiAiType = apiAiType.name,
    status = status.name,
    createdAt = createdAt.toString(),
    idVideo = idVideo
)

enum class QueueGenStatus {
    CREATED,
    GENERATION,
    COMPLETED,
    FAILED,
    ERROR_SAFETY_SYSTEM_HUMAN,
    ERROR_SAFETY_SYSTEM_SEX_CONTENT,
    TERMS_OF_SERVICE,
    TIMEOUT,
    MINOR_CHILDREN,
    CELEBRITIES,
    NOT_ENOUGH_COINS
}