package com.example.voice

import java.util.regex.Pattern

/**
 * Preprocesses and sanitizes text before handing it to the Text-to-Speech engine.
 *
 * Removes technical artifacts, markdown notation, raw JSON, URLs, code blocks,
 * and converts technical API stack traces into conversational assistant notifications.
 */
object NaturalSpeechCleaner {

    private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val MARKDOWN_IMAGE_REGEX = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
    private val MARKDOWN_LINK_REGEX = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
    private val RAW_URL_REGEX = Regex("https?://\\S+")
    private val RAW_JSON_REGEX = Regex("\\{[\\s\\r\\n]*\"[^\"]+\"[\\s\\r\\n]*:[\\s\\S]*?\\}")
    private val EMOJI_AND_SYMBOLS_REGEX = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]|[\\u2600-\\u27BF]|[\\u2300-\\u23FF]|[\\u2B50-\\u2B55]")
    private val MULTIPLE_NEWLINES_REGEX = Regex("[\\r\\n]+")
    private val MULTIPLE_SPACES_REGEX = Regex("[ \\t]+")
    private val REPEATED_PUNCTUATION_REGEX = Regex("([.!?।]){2,}")

    /**
     * Cleans raw text for natural human-like voice synthesis.
     */
    fun cleanForSpeech(rawInput: String): String {
        if (rawInput.isBlank()) return ""

        var text = rawInput.trim()

        // 1. Handle technical API / network exceptions gracefully
        if (isTechnicalError(text)) {
            return "Server se connect karne mein pareshani ho rahi hai. Kripya thodi der baad dobara koshish karein."
        }

        // 2. Remove code blocks (do not read programming code aloud)
        text = text.replace(CODE_BLOCK_REGEX, " Code snippet chat mein uplabdh hai. ")

        // 3. Remove inline code backticks, keeping inner content
        text = text.replace(INLINE_CODE_REGEX, "$1")

        // 4. Strip markdown image tags
        text = text.replace(MARKDOWN_IMAGE_REGEX, "")

        // 5. Clean markdown links: [Title](url) -> Title
        text = text.replace(MARKDOWN_LINK_REGEX, "$1")

        // 6. Remove raw URLs
        text = text.replace(RAW_URL_REGEX, " ")

        // 7. Strip raw JSON payloads
        text = text.replace(RAW_JSON_REGEX, " ")

        // 8. Strip markdown headings (### Header -> Header)
        text = text.replace(Regex("(?m)^#{1,6}\\s*"), "")

        // 9. Clean markdown list markers (*, -, +, 1., 2.) before formatting
        text = text.replace(Regex("(?m)^\\s*[-*+•]\\s+"), "")
        text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")

        // 10. Clean markdown bold, italic, strikethrough: **text**, *text*, __text__, _text_, ~~text~~
        text = text.replace(Regex("\\*\\*([^*\\r\\n]+)\\*\\*"), "$1")
        text = text.replace(Regex("\\*([^*\\r\\n]+)\\*"), "$1")
        text = text.replace(Regex("__([^_\\r\\n]+)__"), "$1")
        text = text.replace(Regex("_([^_\\r\\n]+)_"), "$1")
        text = text.replace(Regex("~~([^~\\r\\n]+)~~"), "$1")

        // 11. Clean table borders and horizontal rules
        text = text.replace(Regex("(?m)^[-=_]{3,}\\s*$"), " ")
        text = text.replace("|", ", ")

        // 12. Remove decorative brackets and symbols (< >, { }, [ ], ~)
        text = text.replace(Regex("[<>{}\\[\\]~^\\\\/]"), " ")

        // 13. Remove emojis & unicode pictographs so TTS doesn't read symbol names
        text = text.replace(EMOJI_AND_SYMBOLS_REGEX, "")

        // 14. Normalize consecutive punctuation and breathing pauses
        text = text.replace(REPEATED_PUNCTUATION_REGEX, "$1")

        // 15. Standardize whitespace
        text = text.replace(MULTIPLE_NEWLINES_REGEX, ". ")
        text = text.replace(MULTIPLE_SPACES_REGEX, " ")

        // 16. Enhance natural assistant conversational rhythm
        text = addNaturalConversationalPauses(text)

        return text.trim()
    }

    /**
     * Checks if the response is a technical system/API crash message.
     */
    private fun isTechnicalError(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("exception:") ||
                lower.contains("fatal error") ||
                lower.contains("http 401") ||
                lower.contains("http 403") ||
                lower.contains("http 500") ||
                lower.contains("http 502") ||
                lower.contains("http 503") ||
                lower.contains("http 429") ||
                lower.contains("java.lang.") ||
                lower.contains("okhttp3.") ||
                lower.contains("retrofit2.") ||
                lower.contains("stacktrace") ||
                lower.contains("econnrefused") ||
                lower.contains("enotfound") ||
                lower.contains("apikeymissing")
    }

    /**
     * Adds natural pauses after conversational affirmations or greetings
     * to prevent speech synthesis from sounding rushed.
     */
    private fun addNaturalConversationalPauses(input: String): String {
        var text = input

        // Insert pause after conversational openers if followed immediately by words without punctuation
        val openers = listOf(
            "Bilkul", "Haan", "Haanji", "Zaroor", "Theek hai", "Achha",
            "Namaste", "Hello", "Sure", "Certainly", "Alright", "Of course"
        )
        for (opener in openers) {
            val pattern = Regex("(?i)^$opener\\s+([A-Za-z\u0900-\u097F])")
            text = text.replace(pattern, "$opener, $1")
        }

        return text
    }
}
