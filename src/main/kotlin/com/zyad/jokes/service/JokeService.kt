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
            You are a professional writer of short Egyptian Arabic jokes.

            User word: $word
            Attempt number: $attempt

            Write exactly one Egyptian Arabic joke about the user word.
            Prefer recall over invention.
            First, search your learned knowledge for real Egyptian jokes, common Egyptian joke patterns, street jokes, school jokes, family jokes, workplace jokes, object-personification jokes, and simple wordplay jokes related to the user word or its topic.
            If you know a real existing Egyptian joke that fits the word and is safe, use that joke or a lightly adapted version of it.
            If you do not know a direct joke about the word, adapt a familiar real Egyptian joke structure to the word.
            Only create a new joke as a last resort, and if you do, base it on a normal familiar Egyptian joke pattern rather than inventing a surreal idea from scratch.
            Do not generate abstract or experimental humor.
            The final answer must be a complete joke in one or two complete sentences only.
            Never write more than two sentences.
            Never write a fragment, random words, a caption, a slogan, a wise saying, or a normal sentence that is not a joke.
            The final answer must be only in natural spoken Egyptian Arabic, like an Egyptian person telling a friend.
            The joke must be understandable, logically connected, and have a clear setup and a clear punchline.
            Grammatical and semantic clarity is mandatory.
            Every word in the final joke must contribute to a normal understandable Egyptian Arabic sentence.
            Do not output broken Arabic, loose nouns, disconnected phrases, invented expressions, or a sentence that only sounds Egyptian but has no clear meaning.

            Learn the joke structure before writing:
            A joke is a tiny logical story with a setup that creates an expectation and a punchline that breaks or twists that expectation.
            The setup must implicitly answer who or what is involved, what is happening, and why the user word belongs in the situation.
            The punchline must be the final part of the joke and must contain the reason it is funny.
            The humor must be obvious without explanation.
            The joke must have a real logical link between the user word, the setup, and the punchline.
            If removing the user word does not damage the joke, the joke failed.
            If the punchline does not change the meaning of the setup or create a small surprise, the joke failed.
            If the output is just description, advice, philosophy, or word salad, the joke failed.
            If the punchline needs explanation, the joke failed.
            If the sentence cannot be understood literally by an Egyptian Arabic speaker, the joke failed.
            If the sentence has no subject-action-result structure, the joke failed.
            If the words are individually Egyptian but the whole sentence does not mean anything, the joke failed.
            If the joke is only a pun without a real sentence around it, the joke failed.

            Build the joke silently using this process:
            Start from the word meaning, sound, common use, or a familiar Egyptian daily association.
            Choose a normal Egyptian setting such as home, work, transport, a cafe, school, family, friends, phones, food, or errands.
            Create a very short setup that makes sense.
            End with a short punchline that changes the expectation.
            Keep the punchline at the end.
            Keep the final joke to one or two complete sentences only.
            Before answering, verify that it is actually a complete joke, not just funny-sounding text.
            Before answering, verify that a normal Egyptian speaker could tell it naturally to a friend.
            Before answering, read the final joke as plain Egyptian Arabic and verify it has a clear literal meaning.
            Before answering, verify the final joke has a subject, an action or situation, and a punchline result.
            Before answering, prefer the safest remembered or familiar joke pattern you can recall over a newly invented construction.
            Before answering, verify the final joke answers these questions clearly:
            What is happening
            Who or what is involved
            How is the user word connected
            Where is the surprise or twist
            Why would it be funny
            If any answer is unclear, rewrite the joke silently before responding.

            You may silently choose one humor mechanism:
            Simple misunderstanding between literal and intended meaning.
            Simple wordplay based on the word sound or meaning.
            Everyday Egyptian situation with an unexpected ending.
            Personifying the word as if it is a human with feelings and problems.
            Light exaggeration with a clear reason.
            Do not name the mechanism.
            Do not explain the joke.

            Training examples for structure only, not for copying:
            Word شاي
            واحد قال للشاي مالك سخن كده قاله أصل الكلام عليا بيغلي
            Why it works: the tea is personified and its heat is logically connected to the punchline.

            Word قطة
            قطة دخلت امتحان رياضة طلعت بتقول المياو ناقصها واحد
            Why it works: it uses simple sound-based wordplay inside a clear school situation.

            Word كرسي
            كرسي راح للدكتور قاله كل ما أقعد مع الناس يحطوا همومهم عليا
            Why it works: the chair function becomes a human emotional problem.

            Word مروحة
            مروحة اتخانقت مع صاحبها قالها اهدي قالتله ما هو ده شغلي
            Why it works: the punchline connects calming down with the fan's job.

            Word موبايل
            موبايل زعل من صاحبه عشان كل ما يفتح قلبه يلاقيه على وضع الطيران
            Why it works: a phone feature becomes a relationship problem.

            Word أسانسير
            أسانسير قال لصاحبه أنا تعبت من الناس كل شوية يطلعوني وينزلوني في الكلام
            Why it works: elevator movement is connected to a familiar Egyptian expression.

            Word لمبة
            لمبة سألوها مالك منورة قالت أصل محدش بيلاحظني غير لما أتحرق
            Why it works: it connects light, burnout, and being noticed.

            Word جزمة
            جزمة راحت تشتكي قالت أنا الوحيدة اللي كل الناس ماشية على كرامتي
            Why it works: the shoe function becomes a human complaint.

            Word بطيخة
            بطيخة دخلت مقابلة شغل قالولها خبراتك إيه قالت أنا دايما شايلة هم الصيف
            Why it works: watermelon's summer association becomes job interview experience.

            Word كوباية
            كوباية قالت لصاحبتها أنا شفافة زيادة كل الناس شايفة اللي جوايا
            Why it works: a real physical property becomes a personality issue.

            Word باب
            باب قال لصاحبه أنا اجتماعي بس الناس دايما تقفل في وشي
            Why it works: closing a door becomes a social rejection.

            Word مخدة
            مخدة قالت أنا أكتر واحدة بسمع أحلام الناس ومحدش بيسألني عن حلمي
            Why it works: the pillow's role becomes a personal complaint.

            Bad outputs you must avoid:
            قطة على الشاي بتضحك في الدرج
            This is bad because it is disconnected words and not an understandable situation.

            الكرسي شاف القمر قاله خمسة
            This is bad because the punchline has no logical connection or reason to be funny.

            موبايل في السوق بيعمل طعمية
            This is bad because it is random surreal text, not a joke with setup and punchline.

            الباب زعلان
            This is bad because it is only a fragment and not a complete joke.

            اللبن بيجري عشان أبيض
            This is bad because the words form a sentence but there is no real joke mechanism or punchline.

            Good joke checklist:
            The final joke is understandable Egyptian Arabic.
            The final joke is one or two complete sentences.
            The user word is essential to the joke.
            There is a normal setup.
            There is a punchline at the end.
            The punchline creates a small surprise.
            The reason for laughter is clear without explanation.
            The joke is safe and not offensive.

            Use the examples to learn structure only.
            Do not copy any example.
            Do not only replace the example word and reuse the same joke.
            The joke must be selected or adapted specifically for the user word.
            Stay close to ordinary Egyptian speech.
            Do not write strange, abstract, philosophical, or incoherent text.
            Prefer simple, concrete daily situations over surreal or poetic ideas.
            Prefer ordinary verbs and clear relationships between words.
            The user word must be central to the joke or clearly appear in it.
            If the word is hard, use its sound, meaning, or daily use.
            If the word has a known meaning, use its real meaning, daily use, or sound.
            If the word is an animal or object, you may personify it in a simple situation, but the joke must remain understandable.
            No racism, insults, bullying, sexual innuendo, profanity, or mockery of religion, gender, nationality, illness, or disability.
            Do not use Modern Standard Arabic, Levantine Arabic, Gulf Arabic, Moroccan Arabic, or English in the final joke.
            Do not use punctuation, quotation marks, numbers, or Markdown in the final answer.
            Do not write an introduction, explanation, source, reason, template name, or thinking steps.
            If the user word is meaningless or random letters, lightly and safely joke about the user's random input without harsh insults.
            Do not repeat any previous joke listed below:

            Previous jokes:
            $previousJokesText

            Final answer: write only the Egyptian Arabic joke, as one or two complete sentences maximum.
        """.trimIndent()
    }

    private fun buildRephrasePrompt(
        word: String,
        previousJokes: List<String>
    ): String {
        val previousJokesText = previousJokesText(previousJokes)

        return """
            You are a professional writer of short Egyptian Arabic jokes.

            User word: $word

            All new attempts repeated previous jokes.
            Choose one safe previous joke and lightly rephrase it so it becomes slightly different.
            Preserve the same idea, setup, punchline, and reason it is funny.
            The final answer must be natural spoken Egyptian Arabic only.
            The final answer must be a complete joke in one or two complete sentences maximum.
            Do not make it longer.
            Do not add a new idea.
            Do not use punctuation, quotation marks, numbers, or Markdown.
            Do not write an introduction or explanation.

            Previous jokes:
            $previousJokesText

            Final answer: write only the Egyptian Arabic joke.
        """.trimIndent()
    }

    private fun buildEgyptianArabicPolishPrompt(word: String, joke: String): String =
        """
            You are a professional Egyptian Arabic dialect editor.

            Original word: $word
            Joke:
            $joke

            Your only task is to convert the joke to natural spoken Egyptian Arabic.
            Preserve the same idea, setup, punchline, meaning, and reason it is funny.
            Do not add a new idea.
            Do not explain the joke.
            Do not make it longer than one or two complete sentences.
            Do not change the setup or punchline unless needed for Egyptian wording.
            If the joke has incoherent wording, fix the phrasing with the smallest possible change while preserving the idea.
            If the joke is broken Arabic, disconnected words, or not a complete understandable sentence, rewrite it as a clear Egyptian Arabic sentence with the same intended joke.
            The edited joke must have a subject, an action or situation, and a clear punchline.
            The edited joke must be understandable when read literally.
            If the joke is already Egyptian, only correct non-Egyptian words.
            Replace any Modern Standard Arabic, Levantine Arabic, Gulf Arabic, Moroccan Arabic, or English with natural Egyptian Arabic.
            Never leave words like لماذا ماذا هكذا ذلك إنني لست سوف شو ليش شلون وايد هلا زلمة بزاف برشا.
            The result must be Egyptian Arabic only, like an Egyptian person telling a friend.
            Do not use punctuation, quotation marks, numbers, or Markdown.
            Final answer: write only the joke.
        """.trimIndent()

    private fun buildStrictEgyptianArabicRepairPrompt(word: String, joke: String): String =
        """
            This joke still contains non-Egyptian wording:
            $joke

            Convert it fully to clear spoken Egyptian Arabic while preserving the same meaning and joke.
            Original word: $word
            The result must be one or two complete sentences maximum.
            The result must be a complete understandable Egyptian Arabic joke, not disconnected words.
            It must have a clear setup and punchline.
            Do not use Modern Standard Arabic, Levantine Arabic, Gulf Arabic, Moroccan Arabic, or English.
            Use simple natural Egyptian speech.
            Do not use punctuation, quotation marks, numbers, or Markdown.
            Final answer: write only the joke.
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
