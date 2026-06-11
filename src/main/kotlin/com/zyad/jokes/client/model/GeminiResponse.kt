package com.zyad.jokes.client.model

data class GeminiResponse(
    val candidates: List<Candidate> = emptyList()
) {
    data class Candidate(
        val content: Content? = null
    )

    data class Content(
        val parts: List<Part> = emptyList()
    )

    data class Part(
        val text: String? = null
    )
}