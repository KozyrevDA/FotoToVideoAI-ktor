package data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoDto(
    @SerialName("id_video") val idVideo: String,
    @SerialName("orientation_type") val orientationType: String,
    @SerialName("created_at") val createdAt: String,
)