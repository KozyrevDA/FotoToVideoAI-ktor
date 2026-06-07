package data.repository

import data.repository.files.ImagesRepositoryImpl
import data.repository.files.VideosFilesRepositoryImpl
import data.repository.postgres.PostgresQueueGenRepository
import data.repository.postgres.PostgresRuStoreBillingRepository
import data.repository.postgres.PostgresUserRepository
import data.repository.postgres.VideosRepositoryImpl
import features.templates.TemplatesReader
import io.ktor.util.logging.*

data class ServerRepository(
    val logger: Logger,
    val user: UserRepository = PostgresUserRepository(),
    val videos: VideosRepository = VideosRepositoryImpl(),
    val videosFiles: VideosFilesRepository = VideosFilesRepositoryImpl(),
    val images: ImagesRepository = ImagesRepositoryImpl(logger),
    val ruStoreBilling: RuStoreBillingRepository = PostgresRuStoreBillingRepository(),
    val templatesReader: TemplatesReader = TemplatesReader(logger = logger),
    val queueGen: QueueGenRepository = PostgresQueueGenRepository()
)