package extensions

import io.ktor.server.application.*
import io.ktor.server.config.*

fun Application.getProperty(path: String) = environment.config.property(path).getString()
fun Application.getProperties(path: String) = environment.config.tryGetStringList(path)
fun Application.getBooleanProperty(path: String) = environment.config.property(path).getString().toBooleanStrictOrNull()