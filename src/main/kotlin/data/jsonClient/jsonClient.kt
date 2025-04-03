package data.jsonClient

import kotlinx.serialization.json.Json

val jsonClient = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}