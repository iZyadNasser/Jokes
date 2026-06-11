package com.zyad.jokes

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class JokeApplication

fun main(args: Array<String>) {
	runApplication<JokeApplication>(*args)
}