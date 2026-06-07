package plugins.routing.images

import data.repository.ServerRepository
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*

fun Routing.images(
    serverRepository: ServerRepository,
    logger: Logger
) {
    get("/internal/image") {
        val token = call.request.queryParameters["token"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val tempImage = serverRepository.images.tempImages[token] ?: return@get call.respond(HttpStatusCode.NotFound)

        if (System.currentTimeMillis() > tempImage.expiresAt) {
            tempImage.file.delete()
            serverRepository.images.tempImages.remove(token)
            logger.info("get(\"/internal/image\"), image expires")
            return@get call.respond(HttpStatusCode.Gone)
        }

        call.respondFile(tempImage.file)
        logger.info("get(\"/internal/image\"), image respond")
    }
}