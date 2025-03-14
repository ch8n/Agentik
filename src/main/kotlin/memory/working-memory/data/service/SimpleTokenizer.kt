package memory.`working-memory`.data.service

import memory.`working-memory`.domain.service.Tokenizer

/**
 * A simple implementation of the Tokenizer interface.
 * This is a naive implementation that splits text on whitespace and punctuation.
 * In a production environment, you would use a proper tokenizer like GPT-2 BPE.
 */
class SimpleTokenizer : Tokenizer {
    
    override fun countTokens(text: String): Int {
        return tokenize(text).size
    }
    
    override fun tokenize(text: String): List<String> {
        // This is a very naive implementation
        // In a real application, you would use a proper tokenizer
        return text.split(SPLIT_PATTERN)
            .filter { it.isNotBlank() }
    }
    
    companion object {
        private val SPLIT_PATTERN = Regex("[\\s\\p{Punct}]")
    }
} 