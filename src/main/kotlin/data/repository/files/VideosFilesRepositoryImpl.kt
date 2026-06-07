package data.repository.files

import data.model.User
import data.model.getId
import data.repository.VideosFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val PATH_VIDEOS = "raw/users/"
private const val PATH_DELETED_VIDEOS = "raw/deleted users/"

class VideosFilesRepositoryImpl : VideosFilesRepository {
    override suspend fun saveGeneratedVideo(
        videoFile: File,
        videoId: String,
        user: User
    ): File {
        return saveFile(
            videoFile = videoFile,
            videoId = videoId,
            user = user
        )
    }

    override suspend fun getVideo(idVideo: String, user: User): File? = withContext(Dispatchers.IO) {
        val uid = user.getId()
        val userDir = File("$PATH_VIDEOS$uid")

        if (!userDir.exists() || !userDir.isDirectory) return@withContext null

        userDir.listFiles()?.forEach { dateDir ->
            if (dateDir.isDirectory) {
                val generatedDir = File(dateDir, "generated")

                if (generatedDir.exists() && generatedDir.isDirectory) {
                    generatedDir.listFiles()?.forEach { file ->
                        if (file.isFile && file.nameWithoutExtension == idVideo) {
                            return@withContext file
                        }
                    }
                }
            }
        }

        return@withContext null
    }

    override suspend fun deleteUser(user: User): Unit = withContext(Dispatchers.IO) {
        val uid = user.getId()
        val userPath = File("$PATH_VIDEOS$uid")
        val deletedUserPath = File("$PATH_DELETED_VIDEOS$uid")
        val dates = userPath.listFiles()?.filter { it.isDirectory } ?: emptyList()

        dates.forEach { dateDir ->
            val generatedDir = File(dateDir, "generated")
            if (generatedDir.exists()) {
                val targetDir = File(deletedUserPath, "${dateDir.name}/generated")
                targetDir.parentFile?.mkdirs()
                generatedDir.renameTo(targetDir)
            }
        }
    }

    private suspend fun saveFile(
        videoFile: File,
        videoId: String,
        user: User,
    ): File = withContext(Dispatchers.IO) {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val uid = user.getId()
        val dir = File("$PATH_VIDEOS${uid}/$date/generated")

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val fileName = "$videoId.mp4"
        val finalVideoFile = File(dir, fileName)

        videoFile.copyTo(finalVideoFile, overwrite = true)

        return@withContext finalVideoFile
    }
}