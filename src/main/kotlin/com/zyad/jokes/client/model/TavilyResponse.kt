package com.zyad.jokes.client.model

data class TavilyResponse(
    val results: List<TavilyResult> = emptyList()
)

data class TavilyResult(
    val title: String = "",
    val url: String = "",
    val content: String = ""
)
