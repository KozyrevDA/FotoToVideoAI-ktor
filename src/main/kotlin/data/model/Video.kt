package data.model

import data.dto.VideoDto
import java.time.LocalDateTime

data class Video(
    val emailReg: String,
    val idVideo: String,
    val isDeleted: Boolean,
    val orientationType: OrientationType,
    val createdAt: LocalDateTime,
)

fun Video.toDto() = VideoDto(
    idVideo = idVideo,
    orientationType = orientationType.name,
    createdAt = createdAt.toString()
)