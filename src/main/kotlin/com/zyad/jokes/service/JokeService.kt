package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.web.JokeResponse
import org.springframework.stereotype.Service

@Service
class JokeService(
    private val llmClient: LlmClient
) {

    fun createJoke(word: String): JokeResponse {
        val joke = formatJoke(
            llmClient.generateJoke(
                buildPrompt(word)
            )
        )

        return JokeResponse(
            word = word,
            joke = joke
        )
    }

    private fun buildPrompt(word: String): String =
        """
            Write one complete sentence joke on the word: "$word".

            Rules:
            - Use only natural Egyptian Arabic.
            - No racism, hateful speech, insults, sexual content, profanity, or inappropriate words.
            - The joke must be one or two complete sentences.
            - Do not use punctuation, quotation marks, numbers, Markdown, or explanations in the output.
            - If the word given to you is not a real word or just random letters, make fun of the user in Egyptian Arabic.
            - Output only the joke in only Egyptian Arabic with Arabic characters.
        """.trimIndent()

    private fun formatJoke(joke: String): String =
        joke
            .replace(Regex("[\\p{P}\\p{S}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
