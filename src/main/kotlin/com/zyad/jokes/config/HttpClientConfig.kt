package com.zyad.jokes.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class HttpClientConfig {

    @Bean
    fun restClient(): RestClient = RestClient.builder().build()
}
