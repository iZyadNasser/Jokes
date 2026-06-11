package com.zyad.jokes.client

import com.zyad.jokes.client.model.SearchResult
import com.zyad.jokes.client.model.TavilyResponse
import com.zyad.jokes.config.TavilyProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class TavilySearchClient(
    private val restClient: RestClient,
    private val properties: TavilyProperties
) : WebSearchClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun searchEgyptianJokes(word: String): List<SearchResult> {
        if (properties.apiKey.isBlank()) {
            throw IllegalStateException("TAVILY_API_KEY is missing")
        }

        val results = searchQueries(word)
            .shuffled()
            .take(properties.queryVariants.coerceIn(1, SEARCH_QUERY_VARIANTS.size))
            .flatMap { query ->
                logger.info("Searching Tavily for Egyptian jokes with query: {}", query)
                callTavily(query)
            }
            .distinctBy { "${it.url}|${it.content.take(120)}" }
            .shuffled()

        logger.info("Collected {} unique Tavily search results for word: {}", results.size, word)

        return results
    }

    private fun callTavily(query: String): List<SearchResult> {
        val requestBody = mapOf(
            "query" to query,
            "search_depth" to "basic",
            "topic" to "general",
            "country" to "egypt",
            "max_results" to properties.maxResults.coerceIn(1, MAX_TAVILY_RESULTS),
            "include_answer" to false,
            "include_raw_content" to false,
            "include_images" to false
        )

        val response = restClient.post()
            .uri("${properties.baseUrl}/search")
            .header("Authorization", "Bearer ${properties.apiKey}")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body<TavilyResponse>()
            ?: throw RuntimeException("Tavily returned an empty response")

        return response.results
            .filter { it.content.isNotBlank() }
            .map {
                SearchResult(
                    title = it.title,
                    url = it.url,
                    content = it.content
                )
            }
    }

    private fun searchQueries(word: String): List<String> =
        SEARCH_QUERY_VARIANTS.map { it.replace("{word}", word) }

    private companion object {
        const val MAX_TAVILY_RESULTS = 20

        val SEARCH_QUERY_VARIANTS = listOf(
            "نكت مصرية قصيرة عن {word}",
            "نكتة مصرية {word}",
            "نكت عن {word} بالمصري",
            "نكت مضحكة قصيرة عن {word}",
            "نكت مصرية مضحكة {word}",
            "نكت قصيرة بالمصري {word}"
        )
    }
}
