package data.repository

import data.model.OrientationType
import data.model.User
import data.model.Video

interface VideosRepository {
    suspend fun saveGeneratedVideo(
        idVideo: String,
        orientationType: OrientationType,
        user: User
    )

    suspend fun getAllInfoVideos(user: User): List<Video>
    suspend fun getInfoVideo(idVideo: String, user: User): Video?
    suspend fun deleteUser(user: User)
    suspend fun deleteVideo(idVideo: String, user: User)
}