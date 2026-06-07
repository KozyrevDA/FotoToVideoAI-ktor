package features.templates

import app.Constants
import data.model.Template
import io.ktor.util.logging.*
import java.io.File

private const val PATH = "raw/templates"

class TemplatesReader(private val logger: Logger) {
    private val templatesList: MutableList<Template> = mutableListOf()

    init {
        templatesList.addAll(loadTemplatesFromFiles())
    }

    fun getTemplatesList(): List<Template> {
        return templatesList
    }

    fun getTemplate(name: String): File? {
        return try {
            val path = templatesList.find { it.path.contains(name, true) }?.path ?: return null
            val template = File("$PATH/$path")

            if (template.exists()) {
                template
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadTemplatesFromFiles(): List<Template> {
        val imagesFile = File("$PATH/templates.json")
        val videosFile = File("$PATH/templates_mp4.json")

        val imagesJson = imagesFile.readText()
        val videosJson = videosFile.readText()

        val imageTemplates: List<Template> = Constants.JSON.decodeFromString(imagesJson)
        val videoTemplates: List<Template> = Constants.JSON.decodeFromString(videosJson)

        return imageTemplates + videoTemplates
    }
}