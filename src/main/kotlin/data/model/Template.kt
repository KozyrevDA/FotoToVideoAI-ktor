package data.model

import data.dto.TemplateDto
import kotlinx.serialization.Serializable

@Serializable
data class Template(
    val nameRu: String,
    val groupRu: String,
    val nameEn: String,
    val groupEn: String,
    val namePt: String,
    val groupPt: String,
    val path: String
)

fun Template.toDto() = TemplateDto(
    nameRu = nameRu,
    groupRu = groupRu,
    nameEn = nameEn,
    groupEn = groupEn,
    namePt = namePt,
    groupPt = groupPt,
    path = path
)