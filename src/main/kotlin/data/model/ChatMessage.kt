package data.model

import data.dto.ChatMessageDto
import data.dto.SenderType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val userPrompt: String?,
    val systemPromt: SystemPromt,
    val orientationType: OrientationType,
    val isUser: Boolean,
    val timestamp: Instant,
    val language: String,
    val apiAi: ApiAiType,
)

fun ChatMessage.toDto() = ChatMessageDto(
    userPrompt = userPrompt,
    systemPromt = systemPromt.toDto(),
    orientationType = orientationType,
    sender = if (isUser) SenderType.USER else SenderType.SERVER,
    timestamp = timestamp,
    language = language,
    apiAi = apiAi.name,
)