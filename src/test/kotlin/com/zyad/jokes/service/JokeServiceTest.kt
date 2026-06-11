package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JokeServiceTest {

    @Test
    fun `creates llm joke and strips punctuation`() {
        val llmClient = CapturingLlmClient("مرة واحد شرب لبن سخن، قال للبقرة استني برة!")
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.word).isEqualTo("لبن")
        assertThat(response.joke).isEqualTo("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        assertThat(llmClient.prompts.first()).contains("Prefer recall over invention")
        assertThat(llmClient.prompts.first()).contains("real Egyptian jokes, common Egyptian joke patterns")
        assertThat(llmClient.prompts.first()).contains("one or two complete sentences only")
        assertThat(llmClient.prompts.first()).contains("clear setup and a clear punchline")
        assertThat(llmClient.prompts.first()).contains("لا توجد نكت سابقة")
        assertThat(llmClient.prompts.last()).contains("convert the joke to natural spoken Egyptian Arabic")
    }

    @Test
    fun `retries when the llm repeats a recent joke for the same word`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "مرة واحد شرب لبن سخن قال للبقرة استني برة",
                "مرة واحد شرب لبن سخن قال للبقرة استني برة",
                "مرة واحد شرب لبن سخن قال للبقرة استني برة",
                "مرة واحد شرب لبن سخن قال للبقرة استني برة",
                "واحد سأل اللبن انت رايح فين قاله داخل في الشاي",
                "واحد سأل اللبن انت رايح فين قاله داخل في الشاي"
            )
        )
        val service = JokeService(llmClient)

        val firstResponse = service.createJoke("لبن")
        val secondResponse = service.createJoke("لبن")

        assertThat(firstResponse.joke).isEqualTo("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        assertThat(secondResponse.joke).isEqualTo("واحد سأل اللبن انت رايح فين قاله داخل في الشاي")
        assertThat(llmClient.prompts).hasSize(6)
        assertThat(llmClient.prompts[2]).contains("مرة واحد شرب لبن سخن قال للبقرة استني برة")
    }

    @Test
    fun `rephrases a previous joke as last resort instead of returning fallback text`() {
        val repeatedJoke = "مرة واحد شرب لبن سخن قال للبقرة استني برة"
        val llmClient = SequentialLlmClient(
            listOf(
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                repeatedJoke,
                "واحد سخن اللبن قوي فطلب من البقرة تستنى برة",
                "واحد سخن اللبن قوي فطلب من البقرة تستنى برة"
            )
        )
        val service = JokeService(llmClient)

        service.createJoke("لبن")
        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("واحد سخن اللبن قوي فطلب من البقرة تستنى برة")
        assertThat(llmClient.prompts).hasSize(10)
        assertThat(llmClient.prompts[8]).contains("All new attempts repeated previous jokes")
        assertThat(llmClient.prompts[8]).contains("lightly rephrase it")
    }

    @Test
    fun `repairs polished jokes that still contain non Egyptian dialect markers`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "لماذا ذهب اللبن إلى المدرسة لأنه عايز يتعلم",
                "لماذا راح اللبن المدرسة عشان يتعلم",
                "اللبن راح المدرسة عشان كان عايز يتعلم"
            )
        )
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("اللبن راح المدرسة عشان كان عايز يتعلم")
        assertThat(llmClient.prompts).hasSize(3)
        assertThat(llmClient.prompts.last()).contains("still contains non-Egyptian wording")
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

    private class SequentialLlmClient(
        private val responses: List<String>
    ) : LlmClient {

        val prompts = mutableListOf<String>()
        private var responseIndex = 0

        override fun generateJoke(prompt: String): String {
            prompts += prompt
            return responses[responseIndex++]
        }
    }
}
