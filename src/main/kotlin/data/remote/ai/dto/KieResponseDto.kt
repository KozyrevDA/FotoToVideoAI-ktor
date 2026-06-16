package data.remote.ai.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VeoGenerateRequest(
    val prompt: String,
    val imageUrls: List<String>,
    val model: String,
    @SerialName("aspect_ratio")
    val aspectRatio: String,
    val enableFallback: Boolean,
    val enableTranslation: Boolean,
    val duration: Int = 8,
)

@Serializable
data class VeoGenerateResponse(
    val code: Int,
    val msg: String,
    val data: VeoTaskData? = null
)

@Serializable
data class VeoTaskData(
    val taskId: String
)

@Serializable
data class VeoTaskResponse(
    val code: Int,
    val msg: String,
    val data: VeoTaskDataResult? = null
)

@Serializable
data class VeoTaskDataResult(
    val taskId: String,
    val successFlag: Int,
    val progress: Int? = null,
    val response: VeoTaskResult? = null
)

@Serializable
data class VeoTaskResult(
    val resultUrls: List<String>? = null,
    val failMsg: String? = null
)