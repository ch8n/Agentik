package data.httpClient

import data.jsonClient.jsonClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

val httpClient = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 120_000  // 30 seconds
        connectTimeoutMillis = 120_000  // Optional: 30s for establishing a connection
        socketTimeoutMillis = 120_000   // Optional: 30s for data transfer
    }
    install(ContentNegotiation) {
        json(jsonClient)
    }
}