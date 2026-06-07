package data.model

import data.dto.SystemPromtDto
import kotlinx.serialization.Serializable

@Serializable
data class SystemPromt(
    val nameFilter: String,
    val secondTemplatePath: String?,
)

fun SystemPromt.toDto() = SystemPromtDto(
    nameFilter = nameFilter,
    secondTemplatePath = secondTemplatePath
)