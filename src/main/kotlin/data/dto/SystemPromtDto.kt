package data.dto

import data.model.SystemPromt
import kotlinx.serialization.Serializable

@Serializable
data class SystemPromtDto(
    val nameFilter: String,
    val secondTemplatePath: String?,
)

fun SystemPromtDto.toModel() = SystemPromt(
    nameFilter = nameFilter,
    secondTemplatePath = secondTemplatePath
)