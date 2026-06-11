package com.zyad.jokes.client

import com.zyad.jokes.config.LlmProperties
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class RoutingLlmClient(
    private val properties: LlmProperties,
    private val geminiClient: GeminiClient,
    private val groqClient: GroqClient,
    private val puterClient: PuterClient,
    private val qwenClient: QwenClient
) : LlmClient {

    override fun generateJoke(prompt: String): String =
        clientForProvider().generateJoke(prompt)

    private fun clientForProvider(): LlmClient =
        when (properties.provider.trim().lowercase()) {
            "gemini" -> geminiClient
            "groq" -> groqClient
            "puter" -> puterClient
            "qwen" -> qwenClient
            else -> throw IllegalArgumentException(
                "Unsupported LLM provider '${properties.provider}'. Use gemini, groq, puter, or qwen."
            )
        }
}
