package plugins.routing.templates

import data.model.toDto
import data.repository.ServerRepository
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*

fun Routing.templates(
    serverRepository: ServerRepository,
    logger: Logger
) {
    get("/templates/all") {
        call.respond(
            HttpStatusCode.OK,
            serverRepository.templatesReader.getTemplatesList()
                .filter { it.path.contains(".png") }
                .map { it.toDto() }
        )
    }

    get("/templates_mp4/all") {
        call.respond(
            HttpStatusCode.OK,
            serverRepository.templatesReader.getTemplatesList()
                .filter { it.path.contains(".mp4") }
                .map { it.toDto() }
        )
    }

    get("/templates/{name}") {
        val name = call.parameters["name"] ?: run {
            call.respond(HttpStatusCode.NotFound, "Incorrect value name")
            logger.info("get(\"/templates/{name}\"), Incorrect value name")
            return@get
        }
        val templateFile = serverRepository.templatesReader.getTemplate(name) ?: run {
            call.respond(HttpStatusCode.NotFound, "Filter not found")
            logger.info("get(\"/templates/{name}\"), Template not found, name: $name")
            return@get
        }

        call.respondFile(templateFile)
    }
}