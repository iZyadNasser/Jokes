package com.zyad.jokes.llm.groq

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "groq")
data class GroqProperties(
    var apiKey: String = "",
    var baseUrl: String = "",
    var primaryModel: String = "",
    var secondaryModel: String = ""
)