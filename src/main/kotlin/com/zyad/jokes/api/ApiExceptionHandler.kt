package com.zyad.jokes.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpServerErrorException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(
        ex: IllegalArgumentException
    ): ResponseEntity<ErrorResponse> {

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                message = ex.message ?: "Invalid request"
            )
        )
    }

    @ExceptionHandler(HttpServerErrorException.ServiceUnavailable::class)
    fun handleGeminiUnavailable(
        ex: HttpServerErrorException.ServiceUnavailable
    ): ResponseEntity<ErrorResponse> {

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
            ErrorResponse(
                status = 503,
                error = "Service Unavailable",
                message = "The joke service is currently busy. Please try again in a few moments."
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(
        ex: Exception
    ): ResponseEntity<ErrorResponse> {

        ex.printStackTrace()

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = 500,
                error = "Internal Server Error",
                message = "Something went wrong."
            )
        )
    }
}