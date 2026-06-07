package plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import data.model.ProviderAuth
import features.security.token.TokenConfig
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureAuthentication(
    config: TokenConfig,
    httpClient: HttpClient,
    googleAuthPhotoToVideoAI: ProviderAuth
) {
    install(Authentication) {
        jwt {
            realm = "ktor users"
            verifier(
                JWT.require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .build()
            )
            validate { jwtCredential ->
                if (jwtCredential.payload.audience.contains(config.audience)) {
                    JWTPrincipal(jwtCredential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }

        oauth("auth-oauth-google-phototovideoai") {
            urlProvider = { googleAuthPhotoToVideoAI.urlProvider }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = googleAuthPhotoToVideoAI.name,
                    authorizeUrl = googleAuthPhotoToVideoAI.authorizeUrl,
                    accessTokenUrl = googleAuthPhotoToVideoAI.accessTokenUrl,
                    requestMethod = HttpMethod.Post,
                    clientId = googleAuthPhotoToVideoAI.clientId,
                    clientSecret = googleAuthPhotoToVideoAI.clientSecret,
                    defaultScopes = googleAuthPhotoToVideoAI.defaultScopes,
                    extraAuthParameters = googleAuthPhotoToVideoAI.extraAuthParameters
                )
            }
            client = httpClient
        }
    }
}