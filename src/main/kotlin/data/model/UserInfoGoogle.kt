package data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfoGoogle(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String,
    @SerialName("verified_email") val verifiedEmail: Boolean,
    @SerialName("name") val name: String,
    @SerialName("given_name") val givenName: String? = null,
    @SerialName("family_name") val familyName: String? = null,
    @SerialName("picture") val picture: String? = null
)