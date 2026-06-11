package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.client.WebSearchClient
import com.zyad.jokes.client.model.SearchResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JokeServiceTest {

    @Test
    fun `creates grounded joke from search results and strips punctuation`() {
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "نكت مصرية",
                    url = "https://example.com/jokes",
                    content = "نكتة عن اللبن"
                )
            )
        )
        val llmClient = CapturingLlmClient("مرة واحد شرب لبن سخن، قال للبقرة استني برة!")
        val service = JokeService(llmClient, webSearchClient)

        val response = service.createJoke("لبن")

        assertThat(response.word).isEqualTo("لبن")
        assertThat(response.joke).isEqualTo("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        assertThat(llmClient.prompts.first()).contains("نكتة عن اللبن")
        assertThat(llmClient.prompts.first()).contains("اختار نكتة واحدة من النتائج والتزم بفكرتها وقلبتها")
        assertThat(llmClient.prompts.first()).contains("لا تخترع فكرة جديدة من غير أصل واضح في النتائج")
        assertThat(llmClient.prompts.first()).contains("ممنوع تخلط بين أكثر من نتيجة")
        assertThat(llmClient.prompts.first()).contains("لازم الناتج النهائي يكون بالعامية المصرية فقط")
        assertThat(llmClient.prompts.last()).contains("مهمتك الوحيدة تحويل النكتة للعامية المصرية الطبيعية الصريحة")
    }

    @Test
    fun `retries when the llm repeats a recent joke for the same word`() {
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "نكت مصرية",
                    url = "https://example.com/jokes",
                    content = "نكت كتير عن اللبن"
                )
            )
        )
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
        val service = JokeService(llmClient, webSearchClient)

        val firstResponse = service.createJoke("لبن")
        val secondResponse = service.createJoke("لبن")

        assertThat(firstResponse.joke).isEqualTo("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        assertThat(secondResponse.joke).isEqualTo("واحد سأل اللبن انت رايح فين قاله داخل في الشاي")
        assertThat(llmClient.prompts).hasSize(6)
        assertThat(llmClient.prompts[2]).contains("مرة واحد شرب لبن سخن قال للبقرة استني برة")
    }

    @Test
    fun `rephrases a previous joke as last resort instead of returning fallback text`() {
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "نكت مصرية",
                    url = "https://example.com/jokes",
                    content = "نكتة واحدة بس عن اللبن"
                )
            )
        )
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
        val service = JokeService(llmClient, webSearchClient)

        service.createJoke("لبن")
        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("واحد سخن اللبن قوي فطلب من البقرة تستنى برة")
        assertThat(llmClient.prompts).hasSize(10)
        assertThat(llmClient.prompts[8]).contains("آخر محاولة فقط")
        assertThat(llmClient.prompts[8]).contains("غيّر ألفاظ بسيطة فقط")
    }

    @Test
    fun `repairs polished jokes that still contain non Egyptian dialect markers`() {
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "نكت عربية",
                    url = "https://example.com/jokes",
                    content = "لماذا ذهب اللبن إلى المدرسة"
                )
            )
        )
        val llmClient = SequentialLlmClient(
            listOf(
                "لماذا ذهب اللبن إلى المدرسة لأنه عايز يتعلم",
                "لماذا راح اللبن المدرسة عشان يتعلم",
                "اللبن راح المدرسة عشان كان عايز يتعلم"
            )
        )
        val service = JokeService(llmClient, webSearchClient)

        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo("اللبن راح المدرسة عشان كان عايز يتعلم")
        assertThat(llmClient.prompts).hasSize(3)
        assertThat(llmClient.prompts.last()).contains("لسه فيها ألفاظ مش مصرية")
    }

    @Test
    fun `allows near source jokes after egyptian arabic polishing`() {
        val copiedJoke = "مرة واحد شرب لبن سخن قال للبقرة استني برة"
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "نكت مصرية",
                    url = "https://example.com/jokes",
                    content = copiedJoke
                )
            )
        )
        val llmClient = SequentialLlmClient(
            listOf(
                copiedJoke,
                copiedJoke
            )
        )
        val service = JokeService(llmClient, webSearchClient)

        val response = service.createJoke("لبن")

        assertThat(response.joke).isEqualTo(copiedJoke)
        assertThat(llmClient.prompts).hasSize(2)
        assertThat(llmClient.prompts.first()).contains("غير الكلمات بأقل قدر ممكن")
    }

    @Test
    fun `prioritizes joke like search results over generic content`() {
        val webSearchClient = StubWebSearchClient(
            listOf(
                SearchResult(
                    title = "فوائد اللبن",
                    url = "https://example.com/milk",
                    content = "اللبن مشروب غني بالكالسيوم والبروتين"
                ),
                SearchResult(
                    title = "نكت مصرية عن اللبن",
                    url = "https://example.com/joke",
                    content = "نكتة عن اللبن مرة واحد شرب لبن سخن"
                )
            )
        )
        val llmClient = CapturingLlmClient("مرة واحد شرب لبن سخن قال للبقرة استني برة")
        val service = JokeService(llmClient, webSearchClient)

        service.createJoke("لبن")

        assertThat(llmClient.prompts.first().indexOf("نكت مصرية عن اللبن"))
            .isLessThan(llmClient.prompts.first().indexOf("فوائد اللبن"))
    }

    private class StubWebSearchClient(
        private val results: List<SearchResult>
    ) : WebSearchClient {

        override fun searchEgyptianJokes(word: String): List<SearchResult> = results
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
