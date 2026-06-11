package com.zyad.jokes.client

interface LlmClient {

    fun generateJoke(prompt: String): String
}
