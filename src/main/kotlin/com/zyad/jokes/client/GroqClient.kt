package com.zyad.jokes.client

import com.zyad.jokes.client.model.LlmResponse
import com.zyad.jokes.config.GroqProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GroqClient(
    private val restClient: RestClient,
    private val properties: GroqProperties
): LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generateJoke(prompt: String): String {

        val models = listOf(
            properties.primaryModel,
            properties.secondaryModel
        )

        var lastException: Exception? = null

        for (model in models) {

            try {

                logger.info("Trying Groq model: {}", model)

                val result = callGroq(
                    model = model,
                    prompt = prompt
                )

                logger.info("Groq model succeeded: {}", model)

                return result

            } catch (ex: Exception) {

                logger.warn(
                    "Groq model failed: {}",
                    model
                )

                lastException = ex
            }
        }

        throw lastException ?: RuntimeException("All Groq models failed")
    }

    private fun callGroq(
        model: String,
        prompt: String
    ): String {

        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            ),
            "temperature" to 0.8
        )

        val response = restClient.post()
            .uri("${properties.baseUrl}/chat/completions")
            .header(
                "Authorization",
                "Bearer ${properties.apiKey}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<LlmResponse>()
            ?: throw RuntimeException("Empty response")

        return response.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw RuntimeException("No text returned")
    }
}
