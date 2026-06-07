package features.security.token.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import features.security.token.TokenClaim
import features.security.token.TokenConfig
import features.security.token.TokenService
import java.util.*

class JwtTokenService : TokenService {
    override fun generate(config: TokenConfig, vararg claims: TokenClaim): String {
        var token = JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withExpiresAt(Date(System.currentTimeMillis() + config.expiresIn))

        claims.forEach { claim ->
            token = token.withClaim(claim.name, claim.value)
        }

        return token.sign(Algorithm.HMAC256(config.secret))
    }

    override fun verify(config: TokenConfig, refreshToken: String): Boolean {
        val decodedJwt = JWT.require(Algorithm.HMAC256(config.secret))
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .build()
            .verify(refreshToken)

        return decodedJwt.audience.contains(config.audience)
    }

    override fun getDecodedJwt(config: TokenConfig, accessToken: String): DecodedJWT? {
        return try {
            JWT.require(Algorithm.HMAC256(config.secret))
                .withAudience(config.audience)
                .withIssuer(config.issuer)
                .build()
                .verify(accessToken)
        } catch (_: Exception) {
            null
        }
    }
}