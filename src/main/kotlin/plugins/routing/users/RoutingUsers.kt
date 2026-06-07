package plugins.routing.users

import data.dto.UserDto
import data.dto.toModel
import data.model.*
import data.repository.ServerRepository
import data.shared.app.TypeApp
import extensions.getClaim
import features.hashing.HashingService
import features.hashing.SaltedHash
import features.security.token.TokenClaim
import features.security.token.TokenConfig
import features.security.token.TokenService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.first
import plugins.routing.videos.PRICE_VIDEO_GENERATION


fun Routing.users(
    serverRepository: ServerRepository,
    hashingService: HashingService,
    tokenService: TokenService,
    tokenAccessConfig: TokenConfig,
    tokenRefreshConfig: TokenConfig,
    authVkConfig: AuthVkConfig,
    schemes: Map<TypeApp, String>,
    httpClient: HttpClient,
    settings: Settings,
    logger: Logger
) {
    suspend fun initUser(
        email: String,
        fullName: String?,
        call: ApplicationCall,
        authMethod: AuthMethod,
        typeApp: TypeApp
    ) {
        var user = when (authMethod) {
            is AuthMethod.VK -> authMethod.idVk?.let {
                serverRepository.user.getUserByIdVk(it)
            } ?: serverRepository.user.getUserByEmailReg(email)

            is AuthMethod.APPLE -> {
                serverRepository.user.getUserByEmailReg(email)
                    ?: serverRepository.user.getUserByIdApple(authMethod.idApple)
            }

            else -> serverRepository.user.getUserByEmailReg(email)
        }
        val scheme = schemes[typeApp]

        if (user == null) {
            user = User(
                name = fullName,
                email = if (authMethod is AuthMethod.APPLE) "" else email,
                emailReg = email,
                pass = null,
                salt = "",
                coins = settings.startCoins.first(),
                authMethod = authMethod.name,
                typeApp = typeApp,
                idVk = if (authMethod is AuthMethod.VK) authMethod.idVk else null,
                idApple = if (authMethod is AuthMethod.APPLE) authMethod.idApple else null
            )
            val accessToken = tokenService.generate(
                config = tokenAccessConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )
            val refreshToken = tokenService.generate(
                config = tokenRefreshConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )

            user.refreshToken = refreshToken
            serverRepository.user.createUser(user)

            when (authMethod) {
                is AuthMethod.GOOGLE -> {
                    call.respondRedirect("/auth/close?accessToken=$accessToken&refreshToken=$refreshToken&scheme=$scheme")
                }

                is AuthMethod.VK -> {
                    call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
                }

                is AuthMethod.APPLE -> {
                    call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
                }

                else -> {}
            }
        } else {
            if (user.pass != null) {
                call.respond(HttpStatusCode.Conflict, "Authentication using email")
                return
            }

            val accessToken = tokenService.generate(
                config = tokenAccessConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )
            val refreshToken = tokenService.generate(
                config = tokenRefreshConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )

            user.refreshToken = refreshToken
            serverRepository.user.updateRefreshToken(user)

            if (authMethod is AuthMethod.VK && user.idVk == null) {
                authMethod.idVk?.let { serverRepository.user.addIdVk(user, it) }
            }

            when (authMethod) {
                is AuthMethod.GOOGLE -> {
                    call.respondRedirect("/auth/close?accessToken=$accessToken&refreshToken=$refreshToken&scheme=$scheme")
                }

                is AuthMethod.VK -> {
                    call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
                }

                is AuthMethod.APPLE -> {
                    call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
                }

                else -> {}
            }
        }
    }

    get("/auth/close") {
        val accessToken = call.parameters["accessToken"]
        val refreshToken = call.parameters["refreshToken"]
        val scheme = call.parameters["scheme"]
        val locale = call.request.headers["Accept-Language"] ?: "en"
        val message = when (locale.substring(0, 2).lowercase()) {
            "ru" -> "Авторизация прошла успешно. Окно можно закрыть."
            "pt" -> "Autenticação bem-sucedida. Você pode fechar a janela."
            else -> "Authentication successful. You can close the window."
        }

        call.respondText(
            """
            <!DOCTYPE html>
            <html lang="$locale">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Authentication Complete</title>
                <style>
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        font-family: Arial, sans-serif;
                        text-align: center;
                    }
                    .message {
                        font-size: 24px;
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="message">$message</div>
                <script>               
                    window.onload = function() {                        
                        window.location.href = "$scheme://auth/callback?accessToken=$accessToken&refreshToken=$refreshToken";
                        setTimeout(() => {
                            window.close();
                        }, 2000);
                    };
                </script>           
            </body>
            </html>
            """.trimIndent(),
            contentType = ContentType.Text.Html
        )
    }

    post("users/signup") {
        val emailRegistration = call.parameters["email"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field email are empty")
            return@post
        }
        val pass = call.parameters["pass"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field password are empty")
            return@post
        }

        if (pass.length < 8) {
            call.respond(HttpStatusCode.Conflict, "Password shorter than 8 characters")
            return@post
        }

        val typeApp = TypeApp.fromString(call.parameters["type_app"])
        val saltedHash = hashingService.generateSaltedHash(pass)
        val user = User(
            name = emailRegistration.split("@").firstOrNull() ?: "",
            email = emailRegistration,
            emailReg = emailRegistration,
            pass = saltedHash.hash,
            salt = saltedHash.salt,
            authMethod = AuthMethod.EMAIL.name,
            typeApp = typeApp
        )

        kotlin.runCatching {
            serverRepository.user.createUser(user)
            val accessToken = tokenService.generate(
                config = tokenAccessConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )
            val refreshToken = tokenService.generate(
                config = tokenRefreshConfig,
                TokenClaim(name = "email_registration", value = user.emailReg)
            )
            user.refreshToken = refreshToken
            serverRepository.user.updateRefreshToken(user)
            call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
        }.onFailure {
            call.respond(HttpStatusCode.Conflict, "Email address is already in use")
        }
    }

    post("users/signin") {
        val email = call.parameters["email"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field email are empty")
            return@post
        }
        val pass = call.parameters["pass"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field password are empty")
            return@post
        }

        val user = serverRepository.user.getUserByEmailReg(email) ?: kotlin.run {
            call.respond(HttpStatusCode.Conflict, "Incorrect username or password")
            return@post
        }

        if (user.pass == null) {
            call.respond(HttpStatusCode.Conflict, "Authentication through a provider")
            return@post
        }

        val isValidPass = hashingService.verify(
            value = pass,
            saltedHash = SaltedHash(hash = user.pass, salt = user.salt)
        )

        if (!isValidPass) {
            call.respond(HttpStatusCode.Conflict, "Incorrect username or password")
            return@post
        }

        val accessToken = tokenService.generate(
            config = tokenAccessConfig,
            TokenClaim(name = "email_registration", value = user.emailReg)
        )
        val refreshToken = tokenService.generate(
            config = tokenRefreshConfig,
            TokenClaim(name = "email_registration", value = user.emailReg)
        )

        user.refreshToken = refreshToken
        serverRepository.user.updateRefreshToken(user)

        call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
    }

    post("users/refresh") {
        var refreshToken = call.parameters["token"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field token are empty")
            return@post
        }
        logger.info("start refresh: $refreshToken")
        val isVerifiedRefreshToken = try {
            logger.info("refresh valid")
            tokenService.verify(config = tokenRefreshConfig, refreshToken = refreshToken)
        } catch (e: Exception) {
            logger.info("refresh not valid ${e.stackTraceToString()}")
            false
        }
        val user = serverRepository.user.getUserByRefToken(refreshToken)

        if (!isVerifiedRefreshToken || user == null) {
            logger.info("refresh ${user?.getId()}, Incorrect token")
            call.respond(HttpStatusCode.Conflict, "Incorrect token")
            return@post
        }
        logger.info("refresh ${user.getId()}, user: ${user.emailReg}")

        val accessToken = tokenService.generate(
            config = tokenAccessConfig,
            TokenClaim(name = "email_registration", value = user.emailReg)
        )
        refreshToken = tokenService.generate(
            config = tokenRefreshConfig,
            TokenClaim(name = "email_registration", value = user.emailReg)
        )

        logger.info("refresh ${user.getId()}, refreshToken")
        user.refreshToken = refreshToken
        serverRepository.user.updateRefreshToken(user)

        call.respond(HttpStatusCode.OK, AuthTokens(accessToken, refreshToken))
        logger.info("refresh ${user.getId()}, HttpStatusCode.OK")
    }

    post("users/auth-vk") {
        var email = call.parameters["email"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field email are empty")
            return@post
        }
        val fullName = call.parameters["full_name"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field full_name are empty")
            return@post
        }
        val token = call.parameters["access_token"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Field access_token are empty")
            return@post
        }
        val idVk = call.parameters["id_vk"]
        val typeApp = TypeApp.fromString(call.parameters["type_app"])
        val checkTokenVK = try {
            val accessToken = when (typeApp) {
                TypeApp.FOTO_TO_VIDEO_AI -> authVkConfig.accessTokenPhotoToVideoAI
            }

            httpClient.get("https://api.vk.ru/method/secure.checkToken") {
                parameter("access_token", accessToken)
                parameter("v", authVkConfig.version)
                parameter("token", token)
            }.body<CheckTokenVK>()
        } catch (e: JsonConvertException) {
            call.respond(HttpStatusCode.Conflict, "Incorrect access_token")
            return@post
        }
        if (checkTokenVK.response.success != 1) {
            call.respond(HttpStatusCode.Conflict, "Incorrect access_token")
            return@post
        }

        if (email.isBlank() || email.isEmpty()) {
            email = "vk_id_$idVk"
        }

        initUser(
            email = email,
            fullName = fullName,
            call = call,
            authMethod = AuthMethod.VK(idVk = idVk),
            typeApp = typeApp
        )
    }

    authenticate("auth-oauth-google-phototovideoai") {
        get("users/signin/google") {
            //Redirects to 'authorizeUrl' automatically
        }

        get("users/callback") {
            call.principal<OAuthAccessTokenResponse.OAuth2>()?.let { principal ->
                val userInfo = httpClient.get("https://www.googleapis.com/oauth2/v2/userinfo") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer ${principal.accessToken}")
                    }
                }.body<UserInfoGoogle>()

                initUser(
                    email = userInfo.email,
                    fullName = userInfo.name,
                    call = call,
                    authMethod = AuthMethod.GOOGLE,
                    typeApp = TypeApp.FOTO_TO_VIDEO_AI
                )
            }
        }
    }

    authenticate {
        get("users/authenticate") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                return@get
            }
            val authMethod = user.authMethod ?: run {
                call.respond(HttpStatusCode.Conflict, "AuthMethod not found")
                return@get
            }

            call.respond(HttpStatusCode.OK, authMethod)
        }

        get("users/delete-account") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                return@get
            }

            serverRepository.user.delete(user)
            serverRepository.videos.deleteUser(user)
            serverRepository.videosFiles.deleteUser(user)
            serverRepository.images.deleteUser(user)
            call.respond(HttpStatusCode.OK)
        }

        post("users/upload/{type}") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@post
            }
            val type = call.parameters["type"]
            when (type) {
                "user" -> {
                    val user = call.receive<UserDto>().toModel(emailRegistration)
                    serverRepository.user.updateFromDevice(user)
                }
            }
        }

        get("users/download/{type}") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val type = call.parameters["type"]
            when (type) {
                "user" -> {
                    val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                        call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                        return@get
                    }
                    call.respond(user.toDto())
                }
            }
        }

        get("users/info") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                return@get
            }

            call.respond(user.toDto())
        }

        get("v2/generation-available") {
            val emailRegistration = call.getClaim("email_registration") ?: run {
                call.respond(HttpStatusCode.BadRequest, "Empty authenticate field")
                return@get
            }
            val user = serverRepository.user.getUserByEmailReg(emailRegistration) ?: run {
                call.respond(HttpStatusCode.Conflict, "User $emailRegistration not found")
                return@get
            }
            val resultCount = user.coins - PRICE_VIDEO_GENERATION

            call.respond(HttpStatusCode.OK, resultCount >= 0)
        }
    }
}