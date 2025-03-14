package memory.`working-memory`.domain.service

/**
 * Interface for tokenizing text and counting tokens.
 * This is used to estimate the size of text in terms of tokens for LLM context windows.
 */
interface Tokenizer {
    /**
     * Counts the number of tokens in a text.
     *
     * @param text The text to count tokens in
     * @return The number of tokens in the text
     */
    fun countTokens(text: String): Int
    
    /**
     * Tokenizes a text into a list of tokens.
     *
     * @param text The text to tokenize
     * @return A list of tokens
     */
    fun tokenize(text: String): List<String>
} 