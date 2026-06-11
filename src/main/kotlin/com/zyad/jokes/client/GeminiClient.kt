package com.zyad.jokes.client

import com.zyad.jokes.client.model.GeminiResponse
import com.zyad.jokes.config.GeminiProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GeminiClient(
    private val restClient: RestClient,
    private val properties: GeminiProperties
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generateJoke(prompt: String): String =
        generateJoke(LlmRequest(userPrompt = prompt))

    override fun generateJoke(request: LlmRequest): String {

        if (properties.apiKey.isBlank()) {
            throw IllegalStateException("GEMINI_API_KEY is missing")
        }

        val models = listOf(
            properties.primaryModel,
            properties.secondaryModel,
            properties.lastResortModel
        )

        var lastException: Exception? = null

        for (model in models) {

            try {

                logger.info("Trying Gemini model: {}", model)

                val joke = callGemini(
                    model = model,
                    request = request
                )

                logger.info("Success using model: {}", model)
                logger.info("Generated joke: {}", joke)

                return joke

            } catch (ex: Exception) {

                logger.warn(
                    "Model {} failed. Trying next model.",
                    model,
                    ex
                )

                lastException = ex
            }
        }

        throw lastException
            ?: RuntimeException("All Gemini models failed")
    }

    private fun callGemini(
        model: String,
        request: LlmRequest
    ): String {

        val requestBody = mutableMapOf<String, Any>(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf(
                            "text" to request.userPrompt.trim()
                        )
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to request.temperature,
                "topP" to 0.9,
                "maxOutputTokens" to request.maxOutputTokens
            )
        )

        if (!request.systemPrompt.isNullOrBlank()) {
            requestBody["systemInstruction"] = mapOf(
                "parts" to listOf(
                    mapOf("text" to request.systemPrompt.trim())
                )
            )
        }

        val response = restClient.post()
            .uri(
                "${properties.baseUrl}/v1beta/models/$model:generateContent?key=${properties.apiKey}"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<GeminiResponse>()
            ?: throw RuntimeException("Gemini response was empty")

        return response.candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            ?.trim()
            ?: throw RuntimeException("Gemini returned no text")
    }
}
