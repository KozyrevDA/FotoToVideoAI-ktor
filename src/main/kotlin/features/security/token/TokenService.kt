package features.security.token

import com.auth0.jwt.interfaces.DecodedJWT

interface TokenService {
    fun generate(config: TokenConfig, vararg claims: TokenClaim): String
    fun verify(config: TokenConfig, refreshToken: String): Boolean
    fun getDecodedJwt(config: TokenConfig, accessToken: String): DecodedJWT?
}