package com.zyad.jokes.client.model

data class LlmResponse(
    val choices: List<Choice> = emptyList()
) {
    data class Choice(
        val message: Message
    )

    data class Message(
        val content: String
    )
}