package com.zyad.jokes.client

import com.zyad.jokes.client.model.LlmResponse
import com.zyad.jokes.config.QwenProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class QwenClient(
    private val restClient: RestClient,
    private val properties: QwenProperties
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generateJoke(prompt: String): String =
        generateJoke(LlmRequest(userPrompt = prompt))

    override fun generateJoke(request: LlmRequest): String {

        val models = listOf(
            properties.primaryModel,
            properties.secondaryModel
        )

        var lastException: Exception? = null

        for (model in models) {
            try {
                logger.info("Trying Qwen model: {}", model)

                val result = callQwen(
                    model = model,
                    request = request
                )

                logger.info("Qwen model succeeded: {}", model)
                return result

            } catch (ex: Exception) {
                logger.warn("Qwen model failed: {}", model, ex)
                lastException = ex
            }
        }

        throw lastException ?: RuntimeException("All Qwen models failed")
    }

    private fun callQwen(model: String, request: LlmRequest): String {

        val requestBody = mapOf(
            "model" to model,
            "messages" to messagesFor(request),
            "temperature" to request.temperature,
            "max_tokens" to request.maxOutputTokens,
            "top_p" to 0.9
        )

        val response = restClient.post()
            .uri("${properties.baseUrl}/chat/completions")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<LlmResponse>()
            ?: throw RuntimeException("Empty response from Qwen")

        return response.choices
            .firstOrNull()
            ?.message
            ?.content
            ?.trim()
            ?: throw RuntimeException("No content returned from Qwen")
    }

    private fun messagesFor(request: LlmRequest): List<Map<String, String>> =
        buildList {
            if (!request.systemPrompt.isNullOrBlank()) {
                add(
                    mapOf(
                        "role" to "system",
                        "content" to request.systemPrompt.trim()
                    )
                )
            }

            add(
                mapOf(
                    "role" to "user",
                    "content" to request.userPrompt.trim()
                )
            )
        }
}
