package com.zyad.jokes.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "groq")
data class GroqProperties(
    var apiKey: String = "",
    var baseUrl: String = "",
    var primaryModel: String = "",
    var secondaryModel: String = ""
)