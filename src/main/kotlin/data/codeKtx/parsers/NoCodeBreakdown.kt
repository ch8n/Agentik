package data.codeKtx.parsers

import kotlinx.serialization.Serializable

@Serializable
object NoCodeBreakdown : CodeBreakDown {
    override val parsableLanguage: String
        get() = "unknown"
}