package com.zyad.jokes.web

import com.zyad.jokes.service.JokeService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private val singleWordRegex = Regex("^[\\p{L}][\\p{L}'-]{0,29}$")

@RestController
@RequestMapping("/api/zyad/jokes")
class JokeController(
    private val jokeService: JokeService
) {
    @GetMapping
    fun getJoke(@RequestParam word: String): ResponseEntity<JokeResponse> {
        val cleanWord = word.trim()

        require(singleWordRegex.matches(cleanWord)) {
            "word must be one word, letters only, max 30 chars"
        }

        return ResponseEntity.ok(jokeService.createJoke(cleanWord))
    }
}