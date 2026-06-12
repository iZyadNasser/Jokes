package com.zyad.jokes.llm.gemini

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gemini")
data class GeminiProperties(
    var apiKey: String = "",
    var baseUrl: String = "",
    var primaryModel: String = "",
    var secondaryModel: String = "",
    var lastResortModel: String = "",
    var timeoutSeconds: Long = 5
)