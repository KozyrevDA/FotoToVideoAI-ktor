package data.model

import data.dto.UserDto
import data.shared.app.TypeApp

data class User(
    val emailReg: String,
    val typeApp: TypeApp,
    val coins: Int = 0,
    val name: String? = null,
    val email: String? = null,
    val pass: String? = null,
    val salt: String = "",
    var refreshToken: String = "",
    val authMethod: String? = null,
    val deviceUid: String? = null,
    val idVk: String? = null,
    val idApple: String? = null,
    val isProcessing: Boolean = false
)

fun User.toDto() = UserDto(
    name = name,
    email = email,
    emailReg = emailReg,
    coins = coins,
    typeApp = typeApp.name,
    idVk = idVk,
    isProcessing = isProcessing
)

fun User.getId() = emailReg