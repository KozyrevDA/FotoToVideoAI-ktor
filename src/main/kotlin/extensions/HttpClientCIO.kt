package extensions

import app.Constants
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

fun initHttpClientCIO(smallTimeOuts: Boolean = false) = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Constants.JSON)
    }

    if (smallTimeOuts) {
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    } else {
        install(HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 600_000
            socketTimeoutMillis = 600_000
        }
    }
}