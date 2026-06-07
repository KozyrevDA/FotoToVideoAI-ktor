package features.billing.rustore.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val code: String,
    val message: String? = null,
    val body: AuthBody? = null,
    val timestamp: String
)

@Serializable
data class AuthBody(
    val jwe: String,
    val ttl: Int
)