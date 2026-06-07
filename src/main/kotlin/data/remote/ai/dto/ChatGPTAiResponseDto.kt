package data.remote.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoTaskDto(
    @SerialName("created_at") val created: Long? = null,
    @SerialName("id") val id: String,
    @SerialName("model") val model: String? = null,
    @SerialName("object") val objectType: String? = null,
    @SerialName("seconds") val seconds: String? = null,
    @SerialName("size") val size: String? = null,
    @SerialName("status") val status: String? = null,
)

@Serializable
data class VideoTaskResultDto(
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("error") val error: VideoErrorDto? = null,
    @SerialName("id") val id: String,
    @SerialName("model") val model: String? = null,
    @SerialName("object") val objectType: String? = null,
    @SerialName("progress") val progress: Int? = null,
    @SerialName("result_url") val resultUrl: String? = null,
    @SerialName("seconds") val seconds: String? = null,
    @SerialName("size") val size: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("video_url") val videoUrl: String? = null
)

@Serializable
data class VideoErrorDto(
    @SerialName("message") val message: String? = null,
    @SerialName("type") val type: String? = null
)