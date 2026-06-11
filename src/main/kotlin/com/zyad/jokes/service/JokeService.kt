package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.web.JokeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class JokeService(
    private val llmClient: LlmClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val recentJokesByWord = ConcurrentHashMap<String, MutableList<String>>()

    fun createJoke(word: String): JokeResponse {
        val previousJokes = previousJokesFor(word)
        val rejectedJokes = mutableListOf<String>()

        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            val joke = polishEgyptianArabic(
                word = word,
                joke = llmClient.generateJoke(
                    buildPrompt(
                        word = word,
                        previousJokes = previousJokes + rejectedJokes,
                        attempt = attempt + 1
                    )
                )
            )
            val repeatedJoke = isRepeatedJoke(joke, previousJokes + rejectedJokes)

            if (joke.isNotBlank() && !repeatedJoke) {
                rememberJoke(word, joke)
                return JokeResponse(
                    word = word,
                    joke = joke
                )
            }

            logger.info(
                "Rejected joke for word '{}' on attempt {}. blank={}, repeated={}: '{}'",
                word,
                attempt + 1,
                joke.isBlank(),
                repeatedJoke,
                joke
            )

            if (joke.isNotBlank()) {
                rejectedJokes += joke
            }
        }

        val fallback = polishEgyptianArabic(
            word = word,
            joke = llmClient.generateJoke(
                buildRephrasePrompt(
                    word = word,
                    previousJokes = previousJokes + rejectedJokes
                )
            )
        )

        logger.info("Generated last-resort rephrased joke for word '{}': '{}'", word, fallback)

        if (fallback.isNotBlank()) {
            rememberJoke(word, fallback)
        }

        return JokeResponse(
            word = word,
            joke = fallback
        )
    }

    private fun buildPrompt(
        word: String,
        previousJokes: List<String>,
        attempt: Int
    ): String {
        val previousJokesText = previousJokesText(previousJokes)

        return """
            أنت كاتب نكت مصرية محترف.

            الكلمة التي أدخلها المستخدم: $word
            رقم المحاولة: $attempt

            اكتب نكتة مصرية أصلية قصيرة عن الكلمة.
            لازم النكتة تكون بالعامية المصرية فقط وبشكل طبيعي كأن مصري بيحكيها لصاحبه.
            لازم تكون نكتة مفهومة وليها إعداد واضح وقفلة واضحة وسبب ضحك واضح.
            قبل ما تكتب اختار في دماغك قالب واحد فقط من دول: سوء فهم بسيط، تلاعب لفظي بسيط، موقف يومي مصري، أو شخصنة الكلمة كأنها بني آدم.
            لا تذكر القالب ولا تشرح النكتة.
            خليك قريب من كلام الناس العادي وماتكتبش كلام عشوائي أو فلسفي أو غريب.
            الكلمة لازم تكون محور النكتة أو تظهر فيها بوضوح.
            النكتة جملة واحدة أو جملتين بالكتير.
            لو الكلمة صعبة اعمل نكتة على صوت الكلمة أو معناها أو استخدامها اليومي.
            ممنوع العنصرية أو الإهانة أو التنمر أو الإيحاء الجنسي أو الألفاظ الخارجة أو السخرية من دين أو جنس أو جنسية أو مرض أو إعاقة.
            ممنوع الفصحى أو الشامي أو الخليجي أو المغربي أو الإنجليزي.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            لا تكتب مقدمة أو شرح أو مصادر.
            لا تكرر أي نكتة من النكت السابقة التالية:

            النكت السابقة:
            $previousJokesText

            اكتب النكتة فقط.
        """.trimIndent()
    }

    private fun buildRephrasePrompt(
        word: String,
        previousJokes: List<String>
    ): String {
        val previousJokesText = previousJokesText(previousJokes)

        return """
            أنت كاتب نكت مصرية محترف.

            الكلمة التي أدخلها المستخدم: $word

            كل المحاولات الجديدة اتكررت.
            اختار نكتة آمنة من النكت السابقة وغيّر ألفاظ بسيطة فقط حتى تبدو مختلفة شوية.
            حافظ على نفس الفكرة ونفس القفلة ونفس سبب الضحك.
            لازم الناتج يكون بالعامية المصرية فقط ومفهوم.
            لا تجعل النكتة أطول ولا تضيف فكرة جديدة.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            لا تكتب مقدمة أو شرح.

            النكت السابقة:
            $previousJokesText

            اكتب النكتة فقط.
        """.trimIndent()
    }

    private fun buildEgyptianArabicPolishPrompt(word: String, joke: String): String =
        """
            أنت محرر لهجة مصرية محترف.

            الكلمة الأصلية: $word
            النكتة:
            $joke

            مهمتك الوحيدة تحويل النكتة للعامية المصرية الطبيعية الصريحة.
            حافظ على نفس فكرة النكتة ونفس الضحكة ونفس المعنى.
            لا تضيف فكرة جديدة ولا تشرح النكتة ولا تجعلها أطول.
            لا تغير الإعداد أو القفلة أو سبب الضحك.
            لو النكتة فيها كلام غير مترابط أو غير مفهوم أصلح الصياغة بأقل تغيير ممكن ومن غير تغيير الفكرة.
            لو النكتة بالفعل مصرية صححها فقط لو فيها لفظ غير مصري.
            استبدل أي فصحى أو شامي أو خليجي أو مغربي أو إنجليزي بتعبير مصري طبيعي.
            ممنوع تسيب كلمات زي لماذا ماذا هكذا ذلك إنني لست سوف شو ليش شلون وايد هلا زلمة بزاف برشا.
            اكتب الناتج بالعامية المصرية فقط كأن مصري بيحكيها لصاحبه.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            اكتب النكتة فقط.
        """.trimIndent()

    private fun buildStrictEgyptianArabicRepairPrompt(word: String, joke: String): String =
        """
            النكتة دي لسه فيها ألفاظ مش مصرية:
            $joke

            حولها بالكامل لعامية مصرية صريحة جدا مع الحفاظ على نفس المعنى والضحكة.
            الكلمة الأصلية: $word
            لا تستخدم فصحى ولا شامي ولا خليجي ولا مغربي ولا إنجليزي.
            استخدم كلام مصري بسيط وطبيعي.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            اكتب النكتة فقط.
        """.trimIndent()

    private fun formatJoke(joke: String): String =
        joke
            .replace(Regex("[\\p{P}\\p{S}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun polishEgyptianArabic(word: String, joke: String): String {
        val formattedJoke = formatJoke(joke)
        if (formattedJoke.isBlank()) {
            return ""
        }

        val polishedJoke = formatJoke(
            llmClient.generateJoke(
                buildEgyptianArabicPolishPrompt(
                    word = word,
                    joke = formattedJoke
                )
            )
        )

        if (polishedJoke.isBlank()) {
            return formattedJoke
        }

        if (!hasNonEgyptianDialectMarkers(polishedJoke)) {
            return polishedJoke
        }

        logger.info(
            "Polished joke for word '{}' still contains non-Egyptian dialect markers: '{}'",
            word,
            polishedJoke
        )

        val repairedJoke = formatJoke(
            llmClient.generateJoke(
                buildStrictEgyptianArabicRepairPrompt(
                    word = word,
                    joke = polishedJoke
                )
            )
        )

        return repairedJoke.ifBlank { polishedJoke }
    }

    private fun previousJokesText(previousJokes: List<String>): String =
        previousJokes
            .distinctBy { normalizeJoke(it) }
            .take(MAX_REMEMBERED_JOKES)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "لا توجد نكت سابقة" }

    private fun previousJokesFor(word: String): List<String> {
        val history = recentJokesByWord[wordKey(word)] ?: return emptyList()

        return synchronized(history) {
            history.toList()
        }
    }

    private fun rememberJoke(word: String, joke: String) {
        val history = recentJokesByWord.computeIfAbsent(wordKey(word)) { mutableListOf() }

        synchronized(history) {
            val normalizedJoke = normalizeJoke(joke)
            history.removeAll { normalizeJoke(it) == normalizedJoke }
            history.add(0, joke)

            while (history.size > MAX_REMEMBERED_JOKES) {
                history.removeAt(history.lastIndex)
            }
        }
    }

    private fun isRepeatedJoke(joke: String, previousJokes: List<String>): Boolean {
        val normalizedJoke = normalizeJoke(joke)

        return previousJokes.any { normalizeJoke(it) == normalizedJoke }
    }

    private fun normalizeJoke(joke: String): String =
        formatJoke(joke).lowercase()

    private fun wordKey(word: String): String =
        word.trim().lowercase()

    private fun hasNonEgyptianDialectMarkers(joke: String): Boolean {
        val normalizedJoke = joke.lowercase()

        return NON_EGYPTIAN_DIALECT_MARKERS.any { marker ->
            Regex("(^|\\s)$marker($|\\s)").containsMatchIn(normalizedJoke)
        }
    }

    private companion object {
        const val MAX_GENERATION_ATTEMPTS = 3
        const val MAX_REMEMBERED_JOKES = 10
        val NON_EGYPTIAN_DIALECT_MARKERS = listOf(
            "لماذا",
            "ماذا",
            "هكذا",
            "ذلك",
            "هذه",
            "إنني",
            "لست",
            "سوف",
            "شو",
            "ليش",
            "شلون",
            "وايد",
            "هلا",
            "زلمة",
            "بزاف",
            "برشا"
        )
    }
}
