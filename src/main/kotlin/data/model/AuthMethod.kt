package data.model

sealed class AuthMethod(val name: String) {
    data object EMAIL : AuthMethod(name = "EMAIL")
    data object GOOGLE : AuthMethod(name = "GOOGLE")
    data class VK(val idVk: String?) : AuthMethod(name = "VK")
    data class APPLE(val idApple: String) : AuthMethod(name = "APPLE")
}