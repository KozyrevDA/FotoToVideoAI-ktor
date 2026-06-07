package data.repository.postgres

import data.model.OrientationType
import data.model.User
import data.model.Video
import data.repository.VideosRepository
import org.jetbrains.exposed.sql.and

class VideosRepositoryImpl : VideosRepository {
    override suspend fun saveGeneratedVideo(
        idVideo: String,
        orientationType: OrientationType,
        user: User
    ): Unit = suspendTransaction {
        VideosDAO.new {
            emailReg = user.emailReg
            this.idVideo = idVideo
            this.orientationType = orientationType.name
            isDeleted = false
        }
    }

    override suspend fun getAllInfoVideos(user: User): List<Video> = suspendTransaction {
        VideosDAO.find {
            (VideosTable.emailReg eq user.emailReg) and
                    (VideosTable.isDeleted eq false)
        }.map(::videosDaoToModel)
    }

    override suspend fun getInfoVideo(idVideo: String, user: User): Video? = suspendTransaction {
        val dao = VideosDAO.find {
            (VideosTable.idVideo eq idVideo) and
                    (VideosTable.emailReg eq user.emailReg) and
                    (VideosTable.isDeleted eq false)
        }.singleOrNull()

        dao?.let { videosDaoToModel(it) }
    }

    override suspend fun deleteUser(user: User) = suspendTransaction {
        VideosDAO.find { VideosTable.emailReg eq user.emailReg }
            .forEach { video ->
                video.isDeleted = true
            }
    }

    override suspend fun deleteVideo(idVideo: String, user: User): Unit = suspendTransaction {
        val dao = VideosDAO.find {
            (VideosTable.idVideo eq idVideo) and (VideosTable.emailReg eq user.emailReg)
        }.singleOrNull()

        dao?.let {
            it.isDeleted = true
        }
    }
}