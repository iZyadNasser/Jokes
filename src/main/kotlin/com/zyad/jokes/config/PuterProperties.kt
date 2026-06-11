package com.zyad.jokes.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "puter")
data class PuterProperties(
    var authToken: String = "",
    var baseUrl: String = "",
    var primaryModel: String = "",
    var secondaryModel: String = ""
)
