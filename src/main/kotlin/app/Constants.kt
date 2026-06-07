package app

import kotlinx.serialization.json.Json

object Constants {
    val JSON = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
}