package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.client.LlmRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JokeServiceTest {

    @Test
    fun `creates joke through setup punchline and polish requests`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "اللبن فضل يسخن على النار",
                "قال للبقرة استني برة عشان الجو مولع",
                "اللبن فضل يسخن على النار قال للبقرة استني برة عشان الجو مولع"
            )
        )
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.word).isEqualTo("لبن")
        assertThat(response.joke).isEqualTo("اللبن فضل يسخن على النار قال للبقرة استني برة عشان الجو مولع.")
        assertThat(llmClient.requests).hasSize(3)
        assertThat(llmClient.requests[0].systemPrompt).contains("أنت كاتب نكت مصري قصير")
        assertThat(llmClient.requests[0].userPrompt).contains("اكتب جملة مصرية قصيرة واحدة")
        assertThat(llmClient.requests[0].temperature).isEqualTo(0.8)
        assertThat(llmClient.requests[1].userPrompt).contains("شكوى أو مفاجأة")
        assertThat(llmClient.requests[1].userPrompt).contains("اللبن فضل يسخن على النار")
        assertThat(llmClient.requests[2].systemPrompt).contains("أنت محرر لهجة مصرية")
    }

    @Test
    fun `retries when the llm repeats a recent joke for the same word`() {
        val firstJoke = "مرة واحد شرب لبن سخن قال للبقرة استني برة"
        val secondJoke = "واحد سأل اللبن انت رايح فين قاله داخل في الشاي"
        val llmClient = SequentialLlmClient(
            listOf(
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                firstJoke,
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                firstJoke,
                "اللبن دخل كوباية شاي",
                "قال أنا واخد وردية سخنة",
                secondJoke
            )
        )
        val service = JokeService(llmClient)

        val firstResponse = service.createJoke("لبن")
        val secondResponse = service.createJoke("لبن")

        assertThat(firstResponse.joke).isEqualTo("$firstJoke.")
        assertThat(secondResponse.joke).isEqualTo("$secondJoke.")
        assertThat(llmClient.requests).hasSize(9)
        assertThat(llmClient.requests[4].userPrompt).contains("$firstJoke.")
        assertThat(llmClient.requests[6].temperature).isEqualTo(0.9)
    }

    @Test
    fun `rephrases a previous joke as last resort instead of returning invalid text`() {
        val repeatedJoke = "مرة واحد شرب لبن سخن قال للبقرة استني برة"
        val rephrasedJoke = "واحد سخن اللبن قوي فطلب من البقرة تستنى برة"
        val llmClient = SequentialLlmClient(
            listOf(
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                repeatedJoke,
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                repeatedJoke,
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                repeatedJoke,
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                repeatedJoke,
                rephrasedJoke,
                rephrasedJoke
            )
        )
        val service = JokeService(llmClient)

        service.createJoke("لبن")
        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("$rephrasedJoke.")
        assertThat(llmClient.requests).hasSize(14)
        assertThat(llmClient.requests[12].userPrompt).contains("اختار فكرة مختلفة")
        assertThat(llmClient.requests[12].temperature).isEqualTo(0.9)
    }

    @Test
    fun `repairs polished jokes that still contain non Egyptian dialect markers`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "اللبن ذهب المدرسة",
                "لأنه عايز يتعلم",
                "لماذا راح اللبن المدرسة عشان يتعلم",
                "اللبن راح المدرسة عشان كان عايز يتعلم"
            )
        )
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("اللبن راح المدرسة عشان كان عايز يتعلم.")
        assertThat(llmClient.requests).hasSize(4)
        assertThat(llmClient.requests.last().userPrompt).contains("لسه فيها ألفاظ مش مصرية")
    }

    @Test
    fun `rejects jokes that do not contain the user word and retries hotter`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "الموبايل فضل يرن",
                "قال محدش سامعني",
                "الموبايل فضل يرن قال محدش سامعني",
                "اللبن فضل يسخن",
                "قال للبقرة استني برة",
                "اللبن فضل يسخن قال للبقرة استني برة"
            )
        )
        val service = JokeService(llmClient)

        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("اللبن فضل يسخن قال للبقرة استني برة.")
        assertThat(llmClient.requests).hasSize(6)
        assertThat(llmClient.requests[3].temperature).isEqualTo(0.9)
        assertThat(llmClient.requests[4].temperature).isEqualTo(0.9)
    }

    @Test
    fun `uses concrete associated object for known abstract words`() {
        val llmClient = SequentialLlmClient(
            listOf(
                "الوردة واقفة عند الباب مستنية الحب",
                "قالت الحب مشهور ومحدش بيجيبلي مية",
                "الحب بعت وردة تشتكي قالت كل الناس بتطلب الحب ومحدش بيسقي الوردة"
            )
        )
        val service = JokeService(llmClient)

        val response = service.createJoke("حب")

        assertThat(response.joke).isEqualTo("الحب بعت وردة تشتكي قالت كل الناس بتطلب الحب ومحدش بيسقي الوردة.")
        assertThat(llmClient.requests.first().userPrompt).contains("وردة")
        assertThat(llmClient.requests.first().userPrompt).contains("الكلمة الأصلية مجردة")
    }

    private class SequentialLlmClient(
        private val responses: List<String>
    ) : LlmClient {

        val requests = mutableListOf<LlmRequest>()
        private var responseIndex = 0

        override fun generateJoke(prompt: String): String =
            generateJoke(LlmRequest(userPrompt = prompt))

        override fun generateJoke(request: LlmRequest): String {
            requests += request
            return responses.getOrElse(responseIndex++) {
                throw AssertionError("No fake LLM response configured for request $responseIndex")
            }
        }
    }
}
