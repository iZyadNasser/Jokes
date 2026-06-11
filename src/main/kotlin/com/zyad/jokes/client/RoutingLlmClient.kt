package com.zyad.jokes.client

import com.zyad.jokes.config.LlmProperties
import com.zyad.jokes.llm.gemini.GeminiClient
import com.zyad.jokes.llm.groq.GroqClient
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Component
@Primary
class RoutingLlmClient(
    private val properties: LlmProperties,
    private val geminiClient: GeminiClient,
    private val groqClient: GroqClient,
) : LlmClient {

    override fun generateJoke(prompt: String): String =
        clientForProvider().generateJoke(prompt)

    private fun clientForProvider(): LlmClient =
        when (properties.provider.trim().lowercase()) {
            "gemini" -> geminiClient
            "groq" -> groqClient
            else -> throw IllegalArgumentException(
                "Unsupported LLM provider '${properties.provider}'. Use gemini or groq."
            )
        }
}
