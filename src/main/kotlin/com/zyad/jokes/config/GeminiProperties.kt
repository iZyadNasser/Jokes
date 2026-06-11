package com.zyad.jokes.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    var apiKey: String = "",
    var baseUrl: String = "",
    var primaryModel: String = "",
    var secondaryModel: String = "",
    var lastResortModel: String = ""
)