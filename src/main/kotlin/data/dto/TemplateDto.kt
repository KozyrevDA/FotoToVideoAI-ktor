package data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TemplateDto(
    val nameRu: String,
    val groupRu: String,
    val nameEn: String,
    val groupEn: String,
    val namePt: String,
    val groupPt: String,
    val path: String,
)