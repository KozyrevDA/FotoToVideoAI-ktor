package data.repository

import data.model.TempImage
import data.model.User
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface ImagesRepository {
    val tempImages: ConcurrentHashMap<String, TempImage>

    suspend fun saveOriginImage(base64: String, user: User)
    suspend fun saveOriginImage(imgBytes: ByteArray, user: User)
    suspend fun saveDoubleOriginImages(
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        notEnoughCoins: Boolean = false
    )

    suspend fun getAllImages(user: User): List<File>
    suspend fun deleteUser(user: User)
    suspend fun generateThumbnail(
        videoFile: File,
        idVideo: String,
        user: User
    )

    suspend fun createTempImage(imgBytes: ByteArray): String
    suspend fun cleanupTempImages()
}