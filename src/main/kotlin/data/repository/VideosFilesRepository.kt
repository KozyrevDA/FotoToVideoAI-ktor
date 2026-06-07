package data.repository

import data.model.User
import java.io.File

interface VideosFilesRepository {
    suspend fun saveGeneratedVideo(
        videoFile: File,
        videoId: String,
        user: User
    ): File

    suspend fun getVideo(idVideo: String, user: User): File?
    suspend fun deleteUser(user: User)
}