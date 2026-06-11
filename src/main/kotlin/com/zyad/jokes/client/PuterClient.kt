package com.zyad.jokes.client

import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.zyad.jokes.config.PuterProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class PuterClient(
    private val restClient: RestClient,
    private val properties: PuterProperties
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val objectMapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        )

    override fun generateJoke(prompt: String): String {
        if (properties.authToken.isBlank()) {
            throw IllegalStateException(
                "PUTER_AUTH_TOKEN is missing. Puter.js can run without a developer key in the browser, " +
                    "but a Spring backend needs a Puter auth token."
            )
        }

        val models = listOf(
            properties.primaryModel,
            properties.secondaryModel
        )

        var lastException: Exception? = null

        for (model in models) {
            try {
                logger.info("Trying Puter model: {}", model)

                val result = callPuter(
                    model = model,
                    prompt = prompt
                )

                logger.info("Puter model succeeded: {}", model)
                return result
            } catch (ex: Exception) {
                logger.warn("Puter model failed: {}", model, ex)
                lastException = ex
            }
        }

        throw lastException ?: RuntimeException("All Puter models failed")
    }

    private fun callPuter(model: String, prompt: String): String {
        val requestBody = mapOf(
            "interface" to "puter-chat-completion",
            "driver" to "ai-chat",
            "method" to "complete",
            "test_mode" to false,
            "auth_token" to properties.authToken,
            "args" to mapOf(
                "messages" to listOf(
                    mapOf("content" to prompt)
                ),
                "model" to model,
                "temperature" to 0.85,
                "max_tokens" to 150,
                "stream" to false
            )
        )
        val requestJson = objectMapper.writeValueAsString(requestBody)

        val responseJson = restClient.post()
            .uri("${properties.baseUrl}/drivers/call")
            .header("Authorization", "Bearer ${properties.authToken}")
            .contentType(MediaType.valueOf("text/plain;actually=json"))
            .body(requestJson)
            .retrieve()
            .body<String>()
            ?: throw RuntimeException("Empty response from Puter")
        val response = objectMapper.readValue<PuterDriverResponse>(responseJson)
        val responseTree = objectMapper.readTree(responseJson)

        if (response.success == false) {
            throw RuntimeException(response.error?.message ?: "Puter driver call failed")
        }

        val content = extractText(responseTree).trim()

        if (content.isBlank()) {
            logger.warn(
                "Puter returned blank content for model {}. Result JSON: {}",
                model,
                responseTree.path("result").toString().take(MAX_LOGGED_RESPONSE_CHARS)
            )
            throw RuntimeException("Blank content returned from Puter")
        }

        return content
    }

    private fun extractText(responseTree: com.fasterxml.jackson.databind.JsonNode): String {
        val directText = listOf(
            "/result/message/content",
            "/result/message/text",
            "/result/content",
            "/result/text",
            "/result/output_text"
        )
            .asSequence()
            .map { responseTree.at(it) }
            .filter { !it.isMissingNode && !it.isNull }
            .mapNotNull { node ->
                when {
                    node.isTextual -> node.asText()
                    node.isArray -> collectText(node)
                    node.isObject -> collectText(node)
                    else -> null
                }
            }
            .firstOrNull { it.isNotBlank() }

        if (!directText.isNullOrBlank()) {
            return directText
        }

        return collectText(responseTree.path("result"))
    }

    private fun collectText(node: com.fasterxml.jackson.databind.JsonNode): String {
        if (node.isTextual) {
            return node.asText()
        }

        if (node.isArray) {
            return node.joinToString(separator = " ") { collectText(it) }
        }

        if (node.isObject) {
            val preferredFields = listOf("content", "text", "output_text")
            val preferredText = preferredFields
                .asSequence()
                .map { node.path(it) }
                .filter { !it.isMissingNode && !it.isNull }
                .map { collectText(it) }
                .firstOrNull { it.isNotBlank() }

            if (!preferredText.isNullOrBlank()) {
                return preferredText
            }
        }

        return ""
    }

    private data class PuterDriverResponse(
        val success: Boolean? = null,
        val result: PuterChatResult? = null,
        val error: PuterError? = null
    )

    private data class PuterChatResult(
        val message: PuterMessage? = null
    )

    private data class PuterMessage(
        val content: String? = null
    )

    private data class PuterError(
        val message: String? = null
    )

    private companion object {
        const val MAX_LOGGED_RESPONSE_CHARS = 2_000
    }
}
