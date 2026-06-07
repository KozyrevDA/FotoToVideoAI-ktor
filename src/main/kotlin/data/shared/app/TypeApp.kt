package data.shared.app

enum class TypeApp {
    FOTO_TO_VIDEO_AI;

    companion object {
        fun fromString(string: String?) = string?.let { valueOf(it) } ?: FOTO_TO_VIDEO_AI
    }
}