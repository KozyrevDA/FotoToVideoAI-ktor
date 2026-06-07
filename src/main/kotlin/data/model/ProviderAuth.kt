package data.model

sealed class ProviderAuth(
    val urlProvider: String,
    val name: String,
    val authorizeUrl: String,
    val accessTokenUrl: String,
    val clientId: String,
    val clientSecret: String,
    val defaultScopes: List<String>,
    val extraAuthParameters: List<Pair<String, String>>
) {
    class GoogleProviderAuth(
        urlProvider: String,
        name: String,
        authorizeUrl: String,
        accessTokenUrl: String,
        clientId: String,
        clientSecret: String,
        defaultScopes: List<String>,
        extraAuthParameters: List<Pair<String, String>>
    ) : ProviderAuth(
        urlProvider = urlProvider,
        name = name,
        authorizeUrl = authorizeUrl,
        accessTokenUrl = accessTokenUrl,
        clientId = clientId,
        clientSecret = clientSecret,
        defaultScopes = defaultScopes,
        extraAuthParameters = extraAuthParameters
    )
}