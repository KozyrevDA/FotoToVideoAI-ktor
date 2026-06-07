package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueueGenDto(
    val uid: String,
    @SerialName("orientation_type") val orientationType: String,
    @SerialName("api_ai_type") val apiAiType: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("id_video") val idVideo: String? = null
)