package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.client.LlmRequest
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
        val wordChoice = wordChoiceFor(word)
        val previousJokes = previousJokesFor(word)
        val rejectedJokes = mutableListOf<String>()

        repeat(MAX_GENERATION_ATTEMPTS) { attemptIndex ->
            val attempt = attemptIndex + 1
            val temperature = if (attempt == 1) DEFAULT_TEMPERATURE else RETRY_TEMPERATURE
            val joke = generateCandidateJoke(
                wordChoice = wordChoice,
                previousJokes = previousJokes + rejectedJokes,
                attempt = attempt,
                temperature = temperature
            )
            val rejectionReason = rejectionReason(
                word = wordChoice.originalWord,
                joke = joke,
                previousJokes = previousJokes + rejectedJokes
            )

            if (rejectionReason == null) {
                rememberJoke(word, joke)
                return JokeResponse(
                    word = word,
                    joke = joke
                )
            }

            logger.info(
                "Rejected joke for word '{}' on attempt {}. reason={}: '{}'",
                word,
                attempt,
                rejectionReason,
                joke
            )

            if (joke.isNotBlank()) {
                rejectedJokes += joke
            }
        }

        val fallback = polishEgyptianArabic(
            word = wordChoice.originalWord,
            joke = llmClient.generateJoke(
                LlmRequest(
                    systemPrompt = JOKE_SYSTEM_PROMPT,
                    userPrompt = buildRephrasePrompt(
                        wordChoice = wordChoice,
                        previousJokes = previousJokes + rejectedJokes
                    ),
                    temperature = RETRY_TEMPERATURE,
                    maxOutputTokens = JOKE_MAX_OUTPUT_TOKENS
                )
            )
        )
        val responseJoke = fallback
            .takeIf {
                rejectionReason(
                    word = wordChoice.originalWord,
                    joke = it,
                    previousJokes = previousJokes
                ) == null
            }
            ?: buildLocalFallbackJoke(wordChoice)

        logger.info("Generated last-resort joke for word '{}': '{}'", word, responseJoke)

        rememberJoke(word, responseJoke)

        return JokeResponse(
            word = word,
            joke = responseJoke
        )
    }

    private fun generateCandidateJoke(
        wordChoice: WordChoice,
        previousJokes: List<String>,
        attempt: Int,
        temperature: Double
    ): String {
        val setup = formatFragment(
            llmClient.generateJoke(
                LlmRequest(
                    systemPrompt = JOKE_SYSTEM_PROMPT,
                    userPrompt = buildSetupPrompt(
                        wordChoice = wordChoice,
                        attempt = attempt
                    ),
                    temperature = temperature,
                    maxOutputTokens = SETUP_MAX_OUTPUT_TOKENS
                )
            )
        )

        if (setup.isBlank()) {
            return ""
        }

        val punchline = formatFragment(
            llmClient.generateJoke(
                LlmRequest(
                    systemPrompt = JOKE_SYSTEM_PROMPT,
                    userPrompt = buildPunchlinePrompt(
                        wordChoice = wordChoice,
                        setup = setup,
                        previousJokes = previousJokes,
                        attempt = attempt
                    ),
                    temperature = temperature,
                    maxOutputTokens = PUNCHLINE_MAX_OUTPUT_TOKENS
                )
            )
        )

        if (punchline.isBlank()) {
            return ""
        }

        return polishEgyptianArabic(
            word = wordChoice.originalWord,
            joke = combineSetupAndPunchline(setup, punchline)
        )
    }

    private fun buildSetupPrompt(
        wordChoice: WordChoice,
        attempt: Int
    ): String =
        """
            اكتب جملة مصرية قصيرة واحدة تصف "${wordChoice.generationWord}" في موقف يومي طبيعي.
            الكلمة الأصلية من المستخدم: "${wordChoice.originalWord}".
            ${abstractWordInstruction(wordChoice)}
            الجملة تكون مفهومة وطبيعية ومش نكتة كاملة.
            مثال لكلمة "موبايل": الموبايل فضل يرن ومحدش يرد
            اكتب الجملة فقط من غير شرح.
            Attempt: $attempt
        """.trimIndent()

    private fun buildPunchlinePrompt(
        wordChoice: WordChoice,
        setup: String,
        previousJokes: List<String>,
        attempt: Int
    ): String =
        """
            اكتب قفلة مصرية مضحكة للتمهيد ده:
            "$setup"

            الكلمة الأصلية لازم تظهر في النكتة: "${wordChoice.originalWord}".
            الكلمة المستخدمة في الموقف: "${wordChoice.generationWord}".
            القفلة لازم تكون شكوى أو مفاجأة مرتبطة بحاجة حقيقية في الكلمة.
            اكتب القفلة فقط، وما تعيدش التمهيد.
            خلي النتيجة النهائية جملة واحدة مفهومة وطبيعية.
            ما تكررش أي نكتة من دول:
            ${previousJokesText(previousJokes)}
            اكتب باللهجة المصرية فقط من غير شرح.
            Attempt: $attempt
        """.trimIndent()

    private fun buildRephrasePrompt(
        wordChoice: WordChoice,
        previousJokes: List<String>
    ): String =
        """
            اكتب نكتة مصرية قصيرة في جملة واحدة عن كلمة "${wordChoice.originalWord}".
            ${abstractWordInstruction(wordChoice)}
            النكتة تجسّم الكلمة وتخليها تشتكي من حاجة حقيقية فيها.
            لازم تذكر كلمة "${wordChoice.originalWord}".
            لازم تنتهي بنقطة.
            اختار فكرة مختلفة عن المحاولات دي أو عيد صياغة واحدة بشكل بسيط:
            ${previousJokesText(previousJokes)}
            اكتب النكتة فقط.
        """.trimIndent()

    private fun abstractWordInstruction(wordChoice: WordChoice): String =
        if (wordChoice.abstractReplacement == null) {
            "لو الكلمة مجردة، اربطها بحاجة يومية ملموسة بدل ما تعمل فكرة فلسفية."
        } else {
            "الكلمة الأصلية مجردة، فاستخدم \"${wordChoice.generationWord}\" كشيء ملموس مرتبط بيها، بس خلي كلمة \"${wordChoice.originalWord}\" تظهر طبيعي في النكتة."
        }

    private fun buildEgyptianArabicPolishPrompt(word: String, joke: String): String =
        """
            حول النص ده لنكتة مصرية طبيعية في جملة واحدة:
            $joke

            الكلمة الأصلية: $word
            حافظ على نفس التمهيد والقفلة والمعنى.
            لازم تذكر الكلمة الأصلية.
            لازم الجملة يكون فيها فاعل وفعل أو موقف واضح.
            لازم تنتهي بنقطة واحدة.
            استخدم لهجة مصرية طبيعية فقط.
            ممنوع الشرح أو علامات الاقتباس أو Markdown.
            اكتب النكتة فقط.
        """.trimIndent()

    private fun buildStrictEgyptianArabicRepairPrompt(word: String, joke: String): String =
        """
            النكتة دي لسه فيها ألفاظ مش مصرية أو صياغة مش طبيعية:
            $joke

            اكتبها باللهجة المصرية الواضحة في جملة واحدة فقط.
            الكلمة الأصلية: $word
            حافظ على نفس المعنى والقفلة.
            لازم تذكر الكلمة الأصلية وتنتهي بنقطة واحدة.
            ممنوع الشرح أو علامات الاقتباس أو Markdown.
            اكتب النكتة فقط.
        """.trimIndent()

    private fun combineSetupAndPunchline(setup: String, punchline: String): String =
        if (normalizeJoke(punchline).startsWith(normalizeJoke(setup))) {
            punchline
        } else {
            "$setup $punchline"
        }

    private fun polishEgyptianArabic(word: String, joke: String): String {
        val formattedJoke = formatJoke(joke)
        if (formattedJoke.isBlank()) {
            return ""
        }

        val polishedJoke = formatJoke(
            llmClient.generateJoke(
                LlmRequest(
                    systemPrompt = POLISH_SYSTEM_PROMPT,
                    userPrompt = buildEgyptianArabicPolishPrompt(
                        word = word,
                        joke = formattedJoke
                    ),
                    temperature = POLISH_TEMPERATURE,
                    maxOutputTokens = JOKE_MAX_OUTPUT_TOKENS
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
                LlmRequest(
                    systemPrompt = POLISH_SYSTEM_PROMPT,
                    userPrompt = buildStrictEgyptianArabicRepairPrompt(
                        word = word,
                        joke = polishedJoke
                    ),
                    temperature = POLISH_TEMPERATURE,
                    maxOutputTokens = JOKE_MAX_OUTPUT_TOKENS
                )
            )
        )

        return repairedJoke.ifBlank { polishedJoke }
    }

    private fun rejectionReason(
        word: String,
        joke: String,
        previousJokes: List<String>
    ): String? =
        when {
            joke.isBlank() -> "blank"
            !containsUserWord(joke, word) -> "missing-user-word"
            !isOneSentenceEndingWithPeriod(joke) -> "not-one-sentence-ending-with-period"
            !hasLikelySubjectAndVerb(joke) -> "missing-subject-or-verb"
            isRepeatedJoke(joke, previousJokes) -> "repeated"
            else -> null
        }

    private fun containsUserWord(joke: String, word: String): Boolean =
        normalizeArabic(joke).contains(normalizeArabic(word))

    private fun isOneSentenceEndingWithPeriod(joke: String): Boolean {
        val trimmed = joke.trim()

        return trimmed.endsWith(".") && trimmed.count { it == '.' } == 1
    }

    private fun hasLikelySubjectAndVerb(joke: String): Boolean {
        val tokens = joke
            .removeSuffix(".")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (tokens.size < MIN_WORDS_WITH_SUBJECT_AND_VERB) {
            return false
        }

        val verbIndex = tokens.indexOfFirst { isLikelyEgyptianVerb(it) }

        return verbIndex >= 0 && tokens.indices.any { index ->
            index != verbIndex && tokens[index].any { it in ARABIC_LETTER_RANGE }
        }
    }

    private fun isLikelyEgyptianVerb(token: String): Boolean {
        val normalizedToken = normalizeArabic(token)
            .removePrefix("و")
            .removePrefix("ف")

        return normalizedToken in COMMON_EGYPTIAN_VERBS ||
            EGYPTIAN_VERB_PREFIXES.any { normalizedToken.startsWith(it) }
    }

    private fun formatFragment(text: String): String =
        text
            .replace(Regex("[\\p{S}]"), "")
            .replace(Regex("[.؟?!،,:;؛\"'`“”‘’\\[\\]{}()<>#*_~|/\\\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun formatJoke(joke: String): String {
        val formatted = joke
            .replace(Regex("[\\p{S}]"), "")
            .replace(Regex("[؟?!]+"), ".")
            .replace(Regex("[،,:;؛\"'`“”‘’\\[\\]{}()<>#*_~|/\\\\]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (formatted.isBlank()) {
            return ""
        }

        return formatted.trimEnd('.') + "."
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
        normalizeArabic(formatFragment(joke)).lowercase()

    private fun normalizeArabic(text: String): String =
        text
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    private fun wordKey(word: String): String =
        normalizeArabic(word)

    private fun hasNonEgyptianDialectMarkers(joke: String): Boolean {
        val normalizedJoke = joke.lowercase()

        return NON_EGYPTIAN_DIALECT_MARKERS.any { marker ->
            Regex("(^|\\s)$marker($|\\s)").containsMatchIn(normalizedJoke)
        }
    }

    private fun wordChoiceFor(word: String): WordChoice {
        val originalWord = word.trim()
        val abstractReplacement = ABSTRACT_WORD_REPLACEMENTS[wordKey(originalWord)]

        return WordChoice(
            originalWord = originalWord,
            generationWord = abstractReplacement ?: originalWord,
            abstractReplacement = abstractReplacement
        )
    }

    private fun buildLocalFallbackJoke(wordChoice: WordChoice): String =
        formatJoke(
            if (wordChoice.abstractReplacement == null) {
                "${wordChoice.originalWord} راح يشتكي قال كل الناس فاكراني بسيطة لحد ما أعمل مشكلة"
            } else {
                "${wordChoice.originalWord} راح يشتكي لل${wordChoice.generationWord} قال كل الناس بتطلبني ومحدش عارف يمسكني"
            }
        )

    private data class WordChoice(
        val originalWord: String,
        val generationWord: String,
        val abstractReplacement: String?
    )

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.8
        const val RETRY_TEMPERATURE = 0.9
        const val POLISH_TEMPERATURE = 0.3
        const val SETUP_MAX_OUTPUT_TOKENS = 60
        const val PUNCHLINE_MAX_OUTPUT_TOKENS = 80
        const val JOKE_MAX_OUTPUT_TOKENS = 120
        const val MAX_GENERATION_ATTEMPTS = 3
        const val MAX_REMEMBERED_JOKES = 10
        const val MIN_WORDS_WITH_SUBJECT_AND_VERB = 4
        val ARABIC_LETTER_RANGE = '\u0600'..'\u06FF'
        const val JOKE_SYSTEM_PROMPT =
            "أنت كاتب نكت مصري قصير. النكتة تكون جملة واحدة عن كلمة معينة. تجسّم الكلمة وتخليها تشتكي من حاجة حقيقية فيها."
        const val POLISH_SYSTEM_PROMPT =
            "أنت محرر لهجة مصرية. تصلح الصياغة بأقل تغيير وتحافظ على القفلة."
        val ABSTRACT_WORD_REPLACEMENTS = mapOf(
            "حب" to "وردة",
            "زمن" to "ساعة",
            "وقت" to "ساعة",
            "خوف" to "ضلمة",
            "حزن" to "منديل",
            "فرح" to "بالونة",
            "امل" to "شمعة",
            "حريه" to "باب",
            "صبر" to "ساعة",
            "كسل" to "كنبة",
            "ذكريات" to "صورة",
            "حلم" to "مخدة",
            "وحده" to "كرسي",
            "حنين" to "صورة"
        )
        val COMMON_EGYPTIAN_VERBS = setOf(
            "قال",
            "قالت",
            "قاله",
            "قالها",
            "راح",
            "راحت",
            "دخل",
            "دخلت",
            "سال",
            "سالت",
            "سألوه",
            "سألوها",
            "اشتكي",
            "اشتكت",
            "زعل",
            "زعلت",
            "فضل",
            "فضلت",
            "اتخانق",
            "اتخانقت",
            "شاف",
            "شافت",
            "لقي",
            "لقت",
            "طلب",
            "طلبت",
            "حط",
            "حطت",
            "فتح",
            "فتحت",
            "قفل",
            "قفلت",
            "رن",
            "تعب",
            "تعبت",
            "عايز",
            "عايزه",
            "ماشي",
            "ماشيه",
            "يمسك",
            "يمسكني"
        )
        val EGYPTIAN_VERB_PREFIXES = listOf(
            "بي",
            "بت",
            "بنت",
            "ات",
            "يت",
            "تت",
            "يشت",
            "تشت"
        )
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
