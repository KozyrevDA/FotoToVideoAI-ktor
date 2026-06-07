package data.dto

import data.model.User
import data.shared.app.TypeApp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val name: String?,
    val email: String?,
    @SerialName("email_reg") val emailReg: String,
    val coins: Int,
    @SerialName("type_app") val typeApp: String? = null,
    @SerialName("id_vk") val idVk: String? = null,
    @SerialName("is_processing") val isProcessing: Boolean = false,
)

fun UserDto.toModel(
    emailReg: String,
    deviceUid: String? = null
) = User(
    name = name,
    emailReg = emailReg,
    coins = coins,
    email = email,
    deviceUid = deviceUid,
    typeApp = TypeApp.fromString(typeApp),
    idVk = idVk,
    isProcessing = isProcessing
)