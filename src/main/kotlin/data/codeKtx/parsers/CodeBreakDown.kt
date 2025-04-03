package data.codeKtx.parsers

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject


object CodeBreakDownSerializer : JsonContentPolymorphicSerializer<CodeBreakDown>(CodeBreakDown::class) {
    override fun selectDeserializer(
        element: JsonElement,
    ): DeserializationStrategy<out CodeBreakDown> {
        val parsableLanguageString = element.jsonObject["topLevelFunctions"]
        val parsableLanguage = when{
            parsableLanguageString != null -> ParsableLanguage.Kotlin
            element.jsonObject.keys.isEmpty() -> ParsableLanguage.Unknown
            else -> ParsableLanguage.Java
        }
        println("""
            CodeBreakDownSerializer
            body -> ${element.jsonObject.keys}
            parsableLanguageString -> $parsableLanguageString
            parsableLanguage -> $parsableLanguage
        """.trimIndent())
        return when (parsableLanguage) {
            ParsableLanguage.Kotlin -> KotlinFileBreakdown.serializer()
            ParsableLanguage.Java -> JavaFileBreakdown.serializer()
            ParsableLanguage.Unknown -> NoCodeBreakdown.serializer()
        }
    }
}

@Serializable(with = CodeBreakDownSerializer::class)
interface CodeBreakDown {
    val parsableLanguage: String
}

