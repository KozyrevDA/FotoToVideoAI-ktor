package data.dto

import data.model.ApiAiType
import data.model.ChatMessage
import data.model.OrientationType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessageDto(
    val userPrompt: String?,
    val systemPromt: SystemPromtDto,
    val timestamp: Instant,
    val sender: SenderType,
    val language: String? = null,
    val orientationType: OrientationType = OrientationType.PORTRAIT,
    val apiAi: String = ApiAiType.VEO_3.name,
)

@Serializable
enum class SenderType {
    USER,
    SERVER
}

fun ChatMessageDto.toModel() = ChatMessage(
    userPrompt = userPrompt,
    systemPromt = systemPromt.toModel(),
    timestamp = timestamp,
    isUser = when (sender) {
        SenderType.USER -> true
        SenderType.SERVER -> false
    },
    language = language ?: "",
    orientationType = orientationType,
    apiAi = ApiAiType.valueOf(apiAi),
)