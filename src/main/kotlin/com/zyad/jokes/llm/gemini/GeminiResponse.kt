package com.zyad.jokes.llm.gemini

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    val promptFeedback: PromptFeedback? = null,
    val usageMetadata: UsageMetadata? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
        val safetyRatings: List<SafetyRating> = emptyList()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Content(
        val parts: List<Part> = emptyList()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Part(
        val text: String? = null,
        val thought: Boolean = false
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PromptFeedback(
        val blockReason: String? = null,
        val safetyRatings: List<SafetyRating> = emptyList()
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SafetyRating(
        val category: String? = null,
        val probability: String? = null,
        val blocked: Boolean? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class UsageMetadata(
        val promptTokenCount: Int? = null,
        val candidatesTokenCount: Int? = null,
        val thoughtsTokenCount: Int? = null,
        val totalTokenCount: Int? = null
    )
}