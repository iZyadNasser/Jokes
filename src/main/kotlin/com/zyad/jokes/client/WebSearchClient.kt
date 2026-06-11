package com.zyad.jokes.client

import com.zyad.jokes.client.model.SearchResult

interface WebSearchClient {

    fun searchEgyptianJokes(word: String): List<SearchResult>
}