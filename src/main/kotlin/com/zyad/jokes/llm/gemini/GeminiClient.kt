package com.zyad.jokes.llm.gemini

import com.fasterxml.jackson.databind.ObjectMapper
import com.zyad.jokes.client.LlmClient
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class GeminiClient(
    private val restClient: RestClient,
    private val properties: GeminiProperties,
    private val objectMapper: ObjectMapper
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generateJoke(prompt: String): String {
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
                val joke = callGemini(model, prompt)
                logger.info("Success using model: {}", model)
                logger.info("Generated joke: {}", joke)
                return joke
            } catch (ex: Exception) {
                logger.warn("Model {} failed. Trying next model.", model, ex)
                lastException = ex
            }
        }

        throw lastException ?: RuntimeException("All Gemini models failed")
    }

    private fun callGemini(model: String, prompt: String): String {
        val requestBody = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.8,
                "topP" to 0.9,
                "maxOutputTokens" to MAX_OUTPUT_TOKENS
            ) + thinkingConfigFor(model)
        )

        val rawJson = restClient.post()
            .uri("${properties.baseUrl}/v1beta/models/$model:generateContent?key=${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<String>()
            ?: throw RuntimeException("Empty response body from Gemini")

        logger.debug("Gemini API raw response for model $model: $rawJson")

        if (rawJson.contains("\"error\"")) {
            throw RuntimeException("Gemini API returned an error: $rawJson")
        }

        val geminiResponse = try {
            objectMapper.readValue(rawJson, GeminiResponse::class.java)
        } catch (e: Exception) {
            throw RuntimeException(
                "Failed to parse Gemini response as GeminiResponse. Raw JSON: $rawJson",
                e
            )
        }

        val candidate = geminiResponse.candidates.firstOrNull()
            ?: throw RuntimeException(
                "Gemini returned no candidates. promptBlockReason=${geminiResponse.promptFeedback?.blockReason}"
            )

        if (candidate.finishReason == "MAX_TOKENS") {
            throw RuntimeException(
                "Gemini output was truncated by maxOutputTokens. " +
                        "model=$model totalTokens=${geminiResponse.usageMetadata?.totalTokenCount} " +
                        "thoughtTokens=${geminiResponse.usageMetadata?.thoughtsTokenCount}"
            )
        }

        val text = candidate.content
            ?.parts
            ?.asSequence()
            ?.filterNot { it.thought }
            ?.mapNotNull { it.text }
            ?.joinToString(separator = "")
            ?.trim()
            .orEmpty()

        if (text.isBlank()) {
            throw RuntimeException(
                "Gemini returned blank text. model=$model finishReason=${candidate.finishReason} " +
                        "promptBlockReason=${geminiResponse.promptFeedback?.blockReason}"
            )
        }

        return text
    }

    private fun thinkingConfigFor(model: String): Map<String, Any> {
        val normalizedModel = model.lowercase()
        return when {
            normalizedModel.startsWith("gemini-3") -> mapOf(
                "thinkingConfig" to mapOf("thinkingLevel" to "minimal")
            )

            normalizedModel.startsWith("gemini-2.5-flash") -> mapOf(
                "thinkingConfig" to mapOf("thinkingBudget" to 0)
            )

            else -> emptyMap()
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 512
    }
}