package knowledge.querybuilder

class Guardrails {

    // DB query question safeguard (example customer DB)
    val checks = listOf(
        "Questions should only pertain to an individual customer.",
        "Questions that may generate long query response should not be asked."
    )
}