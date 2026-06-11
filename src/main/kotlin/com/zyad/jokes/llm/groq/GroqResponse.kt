package com.zyad.jokes.llm.groq

data class GroqResponse(
    val choices: List<Choice> = emptyList()
) {
    data class Choice(
        val message: Message
    )

    data class Message(
        val content: String
    )
}