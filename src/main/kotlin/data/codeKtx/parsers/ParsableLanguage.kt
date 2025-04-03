package data.codeKtx.parsers

enum class ParsableLanguage {
    Kotlin,
    Java,
    Unknown;
    companion object {
        fun fromString(value: String): ParsableLanguage {
            return runCatching { ParsableLanguage.valueOf(value) }
                .getOrDefault(Unknown)
        }
    }
}