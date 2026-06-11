package com.zyad.jokes.client

import com.zyad.jokes.config.GeminiProperties
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

class GeminiClientTest {

    @Test
    fun `requests enough output tokens and joins all non thought text parts`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = GeminiClient(
            restClient = builder.build(),
            properties = geminiProperties(
                primaryModel = "gemini-3.5-flash"
            )
        )

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=test-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().string(
                    allOf(
                        containsString("\"maxOutputTokens\":512"),
                        containsString("\"thinkingConfig\":{\"thinkingLevel\":\"minimal\"")
                    )
                )
            )
            .andRespond(
                withSuccess(
                    """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "كرسي راح للدكتور " },
                                  { "text": "قاله الناس قعدت على أعصابي" }
                                ]
                              },
                              "finishReason": "STOP"
                            }
                          ],
                          "usageMetadata": {
                            "totalTokenCount": 40
                          }
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val joke = client.generateJoke("اكتب نكتة عن كرسي")

        assertThat(joke).isEqualTo("كرسي راح للدكتور قاله الناس قعدت على أعصابي")
        server.verify()
    }

    @Test
    fun `does not return truncated max token response and falls back to next model`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = GeminiClient(
            restClient = builder.build(),
            properties = geminiProperties(
                primaryModel = "gemini-3.5-flash",
                secondaryModel = "gemini-2.5-flash-lite"
            )
        )

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=test-key"))
            .andRespond(
                withSuccess(
                    """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "كرسي راح للدك" }
                                ]
                              },
                              "finishReason": "MAX_TOKENS"
                            }
                          ],
                          "usageMetadata": {
                            "totalTokenCount": 512,
                            "thoughtsTokenCount": 420
                          }
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key=test-key"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(
                content().string(
                    allOf(
                        containsString("\"maxOutputTokens\":512"),
                        containsString("\"thinkingConfig\":{\"thinkingBudget\":0")
                    )
                )
            )
            .andRespond(
                withSuccess(
                    """
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "كرسي راح للدكتور قاله كل الناس بتقعد عليا ومحدش بيسألني" }
                                ]
                              },
                              "finishReason": "STOP"
                            }
                          ]
                        }
                    """.trimIndent(),
                    MediaType.APPLICATION_JSON
                )
            )

        val joke = client.generateJoke("اكتب نكتة عن كرسي")

        assertThat(joke).isEqualTo("كرسي راح للدكتور قاله كل الناس بتقعد عليا ومحدش بيسألني")
        server.verify()
    }

    private fun geminiProperties(
        primaryModel: String,
        secondaryModel: String = "",
        lastResortModel: String = ""
    ): GeminiProperties =
        GeminiProperties(
            apiKey = "test-key",
            baseUrl = "https://generativelanguage.googleapis.com",
            primaryModel = primaryModel,
            secondaryModel = secondaryModel,
            lastResortModel = lastResortModel
        )
}
