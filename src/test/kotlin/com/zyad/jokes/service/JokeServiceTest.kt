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
        assertThat(llmClient.prompts.first()).contains("اكتب نكتة مصرية أصلية قصيرة عن الكلمة")
        assertThat(llmClient.prompts.first()).contains("لازم تكون نكتة مفهومة وليها إعداد واضح وقفلة واضحة")
        assertThat(llmClient.prompts.first()).contains("لا توجد نكت سابقة")
        assertThat(llmClient.prompts.last()).contains("مهمتك الوحيدة تحويل النكتة للعامية المصرية الطبيعية الصريحة")
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
        assertThat(llmClient.prompts[8]).contains("كل المحاولات الجديدة اتكررت")
        assertThat(llmClient.prompts[8]).contains("غيّر ألفاظ بسيطة فقط")
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
        assertThat(llmClient.prompts.last()).contains("لسه فيها ألفاظ مش مصرية")
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
