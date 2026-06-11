package com.zyad.jokes.client

interface LlmClient {

    fun generateJoke(prompt: String): String

    fun generateJoke(request: LlmRequest): String =
        generateJoke(request.combinedPrompt())
}
