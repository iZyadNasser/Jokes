package com.zyad.jokes.client

data class LlmRequest(
    val systemPrompt: String? = null,
    val userPrompt: String,
    val temperature: Double = 0.8,
    val maxOutputTokens: Int = 150
) {

    fun combinedPrompt(): String =
        buildString {
            if (!systemPrompt.isNullOrBlank()) {
                appendLine("System:")
                appendLine(systemPrompt.trim())
                appendLine()
            }

            appendLine("User:")
            append(userPrompt.trim())
        }
}
