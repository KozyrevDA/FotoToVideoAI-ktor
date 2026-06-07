package extensions

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.getClaim(name: String) = principal<JWTPrincipal>()?.getClaim(name, String::class)