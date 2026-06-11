package com.zyad.jokes.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qwen")
data class QwenProperties(
    val apiKey: String,
    val baseUrl: String,
    val primaryModel: String,
    val secondaryModel: String
)