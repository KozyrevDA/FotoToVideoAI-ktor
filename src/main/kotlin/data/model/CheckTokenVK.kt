package data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckTokenVK(
    val response: Response
)

@Serializable
data class Response(
    val date: Long,
    val expire: Int,
    val success: Int,
    @SerialName("user_id") val userId: Int
)