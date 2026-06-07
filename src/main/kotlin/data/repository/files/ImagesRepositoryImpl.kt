package data.repository.files

import data.model.TempImage
import data.model.User
import data.model.getId
import data.repository.ImagesRepository
import io.ktor.util.logging.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private const val PATH_IMAGES = "raw/users/"
private const val PATH_DELETED_IMAGES = "raw/deleted users/"

class ImagesRepositoryImpl(private val logger: Logger) : ImagesRepository {
    override val tempImages = ConcurrentHashMap<String, TempImage>()

    override suspend fun saveOriginImage(base64: String, user: User) {
        saveFile(base64 = base64, user = user, origin = true)
    }

    override suspend fun saveOriginImage(imgBytes: ByteArray, user: User) {
        saveFile(imgBytes = imgBytes, user = user, origin = true)
    }

    override suspend fun saveDoubleOriginImages(
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        notEnoughCoins: Boolean
    ) {
        saveDoubleOriginFiles(
            photo1 = photo1,
            photo2 = photo2,
            user = user,
            notEnoughCoins = notEnoughCoins
        )
    }

    override suspend fun getAllImages(user: User): List<File> = withContext(Dispatchers.IO) {
        val uid = user.getId()
        val userDir = File("$PATH_IMAGES$uid")

        if (!userDir.exists() || !userDir.isDirectory) return@withContext emptyList()

        val images = mutableListOf<File>()
        userDir.listFiles()?.forEach { dateDir ->
            if (dateDir.isDirectory) {
                val generatedDir = File(dateDir, "generated")
                if (generatedDir.exists() && generatedDir.isDirectory) {
                    generatedDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            images.add(file)
                        }
                    }
                }
            }
        }

        return@withContext images.sortedByDescending { it.lastModified() }
    }

    override suspend fun deleteUser(user: User) {
        val email = user.getId()
        val userPath = File("$PATH_IMAGES$email")
        val deletedUserPath = File("$PATH_DELETED_IMAGES$email")
        val dates = userPath.listFiles()?.filter { it.isDirectory } ?: emptyList()

        dates.forEach { dateDir ->
            val generatedDir = File(dateDir, "generated")
            if (generatedDir.exists()) {
                val targetDir = File(deletedUserPath, "${dateDir.name}/generated")
                targetDir.parentFile?.mkdirs()
                generatedDir.renameTo(targetDir)
            }
        }
        userPath.deleteRecursively()
    }

    override suspend fun generateThumbnail(
        videoFile: File,
        idVideo: String,
        user: User
    ): Unit = withContext(Dispatchers.IO) {
        val tempDir = File("raw/tmp")
        if (!tempDir.exists()) tempDir.mkdirs()

        val tempThumbnail = File.createTempFile("thumb_", ".jpg", tempDir)

        val process = ProcessBuilder(
            "ffmpeg",
            "-y",
            "-i", videoFile.absolutePath,
            "-ss", "00:00:01",
            "-vframes", "1",
            tempThumbnail.absolutePath
        )
            .redirectErrorStream(true)
            .start()

        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val error = process.inputStream.bufferedReader().readText()
            throw RuntimeException("FFmpeg failed: $error")
        }

        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val uid = user.getId()
        val dir = File("$PATH_IMAGES$uid/$date/generated")
        if (!dir.exists()) dir.mkdirs()

        val finalFile = File(dir, "thumb_$idVideo.jpg")

        tempThumbnail.copyTo(finalFile, overwrite = true)
        tempThumbnail.delete()
    }

    override suspend fun createTempImage(imgBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        val tempFile = File("raw/tmp/internal/$token.png")
        tempFile.parentFile?.mkdirs()
        tempFile.writeBytes(imgBytes)

        tempImages[token] = TempImage(
            file = tempFile,
            expiresAt = System.currentTimeMillis() + 20 * 60_000
        )

        return@withContext "https://webhook.fototovideoai.store/internal/image?token=$token"
    }

    override suspend fun cleanupTempImages() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expiredTokens = tempImages
            .filterValues { it.expiresAt <= now }
            .keys

        expiredTokens.forEach { token ->
            val tempImage = tempImages.remove(token)
            try {
                tempImage?.file?.delete()
            } catch (e: Exception) {
                logger.warn("Failed to delete temp image: ${tempImage?.file}", e)
            }
        }

        if (expiredTokens.isNotEmpty()) {
            logger.info("Cleaned ${expiredTokens.size} temp images")
        }
    }

    private suspend fun saveFile(
        base64: String,
        user: User,
        origin: Boolean
    ) = withContext(Dispatchers.IO) {
        val imageBytes = Base64.getDecoder().decode(base64)
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val uid = user.getId()
        val dir = File("$PATH_IMAGES${uid}/$date/${if (origin) "origin" else "generated"}")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "image_${System.currentTimeMillis()}.png"
        val file = File(dir, fileName)

        file.writeBytes(imageBytes)
    }

    private suspend fun saveFile(
        imgBytes: ByteArray,
        user: User,
        origin: Boolean
    ) = withContext(Dispatchers.IO) {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val uid = user.getId()
        val dir = File("$PATH_IMAGES${uid}/$date/${if (origin) "origin" else "generated"}")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "image_${System.currentTimeMillis()}.png"
        val file = File(dir, fileName)

        file.writeBytes(imgBytes)
    }

    private suspend fun saveDoubleOriginFiles(
        photo1: ByteArray?,
        photo2: ByteArray?,
        user: User,
        notEnoughCoins: Boolean
    ) = withContext(Dispatchers.IO) {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val uid = user.getId()
        val timeMillis = System.currentTimeMillis()
        val dir = if (notEnoughCoins && photo2 == null) {
            File("$PATH_IMAGES${uid}/$date/origin")
        } else {
            File("$PATH_IMAGES${uid}/$date/origin/$timeMillis")
        }

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val necString = if (notEnoughCoins) "_not_enough_coins" else ""
        val filePhoto1 = File(dir, "image_photo1_$timeMillis${necString}.png")
        val filePhoto2 = File(dir, "image_photo2_$timeMillis${necString}.png")

        photo1?.let { filePhoto1.writeBytes(it) }
        photo2?.let { filePhoto2.writeBytes(it) }
    }
}