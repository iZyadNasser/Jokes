package com.zyad.jokes.service

import com.zyad.jokes.client.LlmClient
import com.zyad.jokes.client.WebSearchClient
import com.zyad.jokes.client.model.SearchResult
import com.zyad.jokes.web.JokeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class JokeService(
    private val llmClient: LlmClient,
    private val webSearchClient: WebSearchClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val recentJokesByWord = ConcurrentHashMap<String, MutableList<String>>()

    fun createJoke(word: String): JokeResponse {
        val searchResults = webSearchClient.searchEgyptianJokes(word)
        logSearchResults(word, searchResults)

        val previousJokes = previousJokesFor(word)
        val rejectedJokes = mutableListOf<String>()

        repeat(MAX_GENERATION_ATTEMPTS) { attempt ->
            val prompt = buildPrompt(
                word = word,
                searchResults = selectPromptResults(word, searchResults),
                previousJokes = previousJokes + rejectedJokes,
                attempt = attempt + 1
            )
            val joke = polishEgyptianArabic(
                word = word,
                joke = llmClient.generateJoke(prompt)
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

        val fallbackPrompt = buildRephrasePrompt(
            word = word,
            searchResults = selectPromptResults(word, searchResults),
            previousJokes = previousJokes + rejectedJokes
        )
        val fallback = polishEgyptianArabic(
            word = word,
            joke = llmClient.generateJoke(fallbackPrompt)
        )

        logger.info(
            "Generated last-resort rephrased joke for word '{}': '{}'",
            word,
            fallback
        )

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
        searchResults: List<SearchResult>,
        previousJokes: List<String>,
        attempt: Int
    ): String {
        val resultsText = searchResults
            .take(MAX_SEARCH_RESULTS_IN_PROMPT)
            .mapIndexed { index, result ->
                """
                نتيجة ${index + 1}
                العنوان: ${result.title}
                الرابط: ${result.url}
                النص: ${result.content}
                """.trimIndent()
            }
            .joinToString(separator = "\n\n")
            .ifBlank { "لا توجد نتائج بحث مفيدة" }

        val previousJokesText = previousJokes
            .take(MAX_REMEMBERED_JOKES)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "لا توجد نكت سابقة" }

        return """
            أنت محرر نكت مصرية.

            الكلمة التي أدخلها المستخدم: $word
            رقم المحاولة الحالي: $attempt

            استخدم نتائج البحث كمصادر لنكت حقيقية أو أفكار نكت واضحة.
            اختار نكتة واحدة من النتائج والتزم بفكرتها وقلبتها.
            لا تخترع فكرة جديدة من غير أصل واضح في النتائج.
            اختار نتيجة واحدة فقط من النتائج التي تحتوي على نكتة أو فكرة نكتة مفهومة عن الكلمة.
            تجاهل أي نتيجة شكلها مقال أو تعريف أو كلام عام أو غير مضحك.
            بعد الفلترة اختار عشوائيا من النتائج الصالحة فقط وليس من كل النتائج.
            غير الكلمات بأقل قدر ممكن فقط لو محتاجة تتحول لعامية مصرية أو تبقى أوضح.
            ممنوع تغير الإعداد أو القفلة أو سبب الضحك.
            ممنوع تخلط بين أكثر من نتيجة لأن ده غالبا بيطلع كلام بلا معنى.
            لو وجدت أكثر من نكتة صالحة لا تختار نفس النكت السابقة واختر نكتة أخرى من النتائج.
            فضّل النكت القصيرة والواضحة والمرتبطة بالكلمة.
            استبعد أي نكتة فيها عنصرية أو إهانة أو تنمر أو إيحاء جنسي أو ألفاظ خارجة أو سخرية من دين أو جنس أو جنسية أو مرض أو إعاقة.
            لو النكتة طويلة اختصرها بدون تغيير الفكرة.
            لازم الناتج النهائي يكون بالعامية المصرية فقط وبشكل طبيعي جدا كأن مصري بيحكيها لصاحبه.
            لو النكتة الأصلية بالفصحى أو بلهجة عربية غير مصرية حولها بالكامل للعامية المصرية قبل الإخراج.
            لو النكتة الأصلية بلغة غير العربية ترجمها وحولها بالكامل للعامية المصرية قبل الإخراج.
            ممنوع تترك أي كلمة أو تركيب واضح من لهجة غير مصرية لو له بديل مصري طبيعي.
            استخدم تعبيرات مصرية بسيطة عند الحاجة مثل مرة واحد ايه ده يا عم ده بتاع بس من غير حشو.
            تأكد أن النكتة المختارة لها معنى وليست مجرد كلام بلا معنى.
            النكتة لازم يكون فيها إعداد واضح وقفلة مفهومة.
            اكتب نكتة واحدة فقط.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            لا تكتب شرحا أو مقدمة أو مصادر.
            لا تكرر أي نكتة من النكت السابقة التالية حتى لو كانت أفضل نتيجة:

            النكت السابقة:
            $previousJokesText

            نتائج البحث:
            $resultsText
        """.trimIndent()
    }

    private fun buildRephrasePrompt(
        word: String,
        searchResults: List<SearchResult>,
        previousJokes: List<String>
    ): String {
        val resultsText = searchResults
            .take(MAX_SEARCH_RESULTS_IN_PROMPT)
            .mapIndexed { index, result ->
                """
                نتيجة ${index + 1}
                العنوان: ${result.title}
                الرابط: ${result.url}
                النص: ${result.content}
                """.trimIndent()
            }
            .joinToString(separator = "\n\n")
            .ifBlank { "لا توجد نتائج بحث مفيدة" }

        val previousJokesText = previousJokes
            .distinctBy { normalizeJoke(it) }
            .take(MAX_REMEMBERED_JOKES)
            .joinToString(separator = "\n") { "- $it" }
            .ifBlank { "لا توجد نكت سابقة" }

        return """
            أنت محرر نكت مصرية.

            الكلمة التي أدخلها المستخدم: $word

            حاولنا استخراج نكتة جديدة من نتائج البحث لكن النكت المناسبة خلصت أو اتكررت.
            دي آخر محاولة فقط.
            اختار نكتة آمنة من النكت السابقة أو من نتائج البحث وغيّر ألفاظ بسيطة فقط.
            لازم تحافظ على نفس الفكرة والمعنى وأن تكون النكتة مفهومة.
            لا تغير النكتة لشيء غير مرتبط بالكلمة.
            لازم الناتج النهائي يكون بالعامية المصرية فقط وبشكل طبيعي جدا كأن مصري بيحكيها لصاحبه.
            لو الأصل بالفصحى أو بلهجة غير مصرية أو بلغة غير عربية حوله بالكامل للعامية المصرية.
            ممنوع تغير الإعداد أو القفلة أو سبب الضحك.
            لا تستخدم علامات ترقيم ولا تنصيص ولا أرقام ولا Markdown.
            لا تكتب شرحا أو مقدمة أو مصادر.
            اكتب نكتة واحدة فقط.

            النكت السابقة:
            $previousJokesText

            نتائج البحث:
            $resultsText
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

    private fun selectPromptResults(word: String, searchResults: List<SearchResult>): List<SearchResult> {
        val (likelyRelevantResults, otherResults) = searchResults
            .filter { it.content.isNotBlank() }
            .partition { isLikelyRelevantJokeResult(word, it) }

        return (likelyRelevantResults.shuffled() + otherResults.shuffled())
            .take(MAX_SEARCH_RESULTS_IN_PROMPT)
    }

    private fun isLikelyRelevantJokeResult(word: String, result: SearchResult): Boolean {
        val text = "${result.title} ${result.content}".lowercase()
        val normalizedWord = word.lowercase()

        return text.contains(normalizedWord) && JOKE_RESULT_MARKERS.any { text.contains(it) }
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

    private fun logSearchResults(word: String, searchResults: List<SearchResult>) {
        logger.info("Found {} search results for word: {}", searchResults.size, word)

        searchResults.forEachIndexed { index, result ->
            logger.info(
                "Search result {} for word '{}': title='{}', url='{}', content='{}'",
                index + 1,
                word,
                result.title.take(120),
                result.url,
                result.content.take(300)
            )
        }
    }

    private companion object {
        const val MAX_GENERATION_ATTEMPTS = 3
        const val MAX_REMEMBERED_JOKES = 10
        const val MAX_SEARCH_RESULTS_IN_PROMPT = 12
        val JOKE_RESULT_MARKERS = listOf(
            "نكت",
            "نكتة",
            "مضحك",
            "ضحك",
            "قفشة",
            "افيه",
            "إفيه"
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
