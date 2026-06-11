package com.zyad.jokes.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tavily")
data class TavilyProperties(
    var apiKey: String = "",
    var baseUrl: String = "",
    var maxResults: Int = 20,
    var queryVariants: Int = 2
)
