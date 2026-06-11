package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JokeServiceTest {

    @Test
    fun `creates llm joke with simple prompt rules`() {
        val llmClient = CapturingLlmClient("مرة واحد شرب لبن سخن، قال للبقرة استني برة!")
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.word).isEqualTo("لبن")
        assertThat(response.joke).isEqualTo("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        assertThat(llmClient.prompts).hasSize(1)
        assertThat(llmClient.prompts.first()).contains("Use only natural Egyptian Arabic")
        assertThat(llmClient.prompts.first()).contains("No racism, hateful speech")
        assertThat(llmClient.prompts.first()).contains("one or two complete sentences")
        assertThat(llmClient.prompts.first()).contains("Do not use punctuation")
        assertThat(llmClient.prompts.first()).contains("لبن")
    }

    @Test
    fun `normalizes whitespace after stripping punctuation`() {
        val llmClient = CapturingLlmClient("  كرسي؟   راح للدكتور... قاله تعبت   ")
        val service = JokeService(llmClient)

        val response = service.createJoke("كرسي")

        assertThat(response.joke).isEqualTo("كرسي راح للدكتور قاله تعبت")
    }

    private class CapturingLlmClient(
        private val response: String
    ) : LlmClient {

        val prompts = mutableListOf<String>()

        override fun generateJoke(prompt: String): String {
            prompts += prompt
            return response
        }
    }
}
