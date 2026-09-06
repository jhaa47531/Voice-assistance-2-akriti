package com.example.voice

import java.util.Locale

/**
 * Detects Hinglish (mixed Hindi-English in Roman script) and normalizes conversational
 * Hindi phrases into Devanagari phonemes while preserving English tech and brand terms.
 *
 * This allows the high-quality Hindi neural TTS voice to pronounce Hindi phrases with
 * authentic phonology and cadence, while naturally articulating English loanwords.
 */
object HinglishSpeechNormalizer {

    // Common Hinglish keywords used to detect conversational Hinglish
    private val HINGLISH_MARKERS = setOf(
        "bilkul", "main", "mai", "aap", "aapka", "aapki", "aapke", "tum",
        "karo", "kare", "karein", "karna", "karta", "karti", "khol", "kholo",
        "rahi", "raha", "rahe", "hoon", "hun", "hai", "hain", "hoga", "hogi",
        "bhejo", "bhej", "kar", "diya", "gaya", "gayi", "shukriya", "dhanyawad",
        "namaste", "alvida", "accha", "achha", "theek", "batao", "bataiye", "bolo",
        "kripya", "chalao", "lagao", "kijiye", "bhi", "yeh", "ye", "woh", "wo",
        "kya", "kyun", "kab", "kahan", "kaun", "mera", "meri", "mere", "hum",
        "nahi", "nahin", "haan", "ha", "sun", "baat", "radd", "madad", "samay",
        "dopahar", "shaam", "raat", "subah", "kal", "aaj", "ab", "lekin", "aur"
    )

    // Common multi-word Hinglish conversational phrases
    private val PHRASE_MAPPINGS = listOf(
        "kar rahi hoon" to "कर रही हूँ",
        "kar raha hoon" to "कर रहा हूँ",
        "kar rahi hu" to "कर रही हूँ",
        "kar raha hu" to "कर रहा हूँ",
        "kar di gayi hai" to "कर दी गई है",
        "kar diya gaya hai" to "कर दिया गया है",
        "kar diya hai" to "कर दिया है",
        "kar di hai" to "कर दी है",
        "kar sakti hoon" to "कर सकती हूँ",
        "kar sakta hoon" to "कर सकता हूँ",
        "khol rahi hoon" to "खोल रही हूँ",
        "khol diya gaya hai" to "खोल दिया गया है",
        "khol diya hai" to "खोल दिया है",
        "khol diya" to "खोल दिया",
        "bhej rahi hoon" to "भेज रही हूँ",
        "bhej diya gaya hai" to "भेज दिया गया है",
        "bhej diya hai" to "भेज दिया है",
        "lagaya ja raha hai" to "लगाया जा रहा है",
        "radd kar diya gaya hai" to "रद्द कर दिया गया है",
        "shuru kar diya gaya hai" to "शुरू कर दिया गया है",
        "band kar diya gaya hai" to "बंद कर दिया गया है",
        "set kar diya gaya hai" to "set कर दिया गया है",
        "set ho gaya hai" to "set हो गया है",
        "theek hai" to "ठीक है",
        "dhyan rakhein" to "ध्यान रखें",
        "kaise madad kar sakti hoon" to "कैसे मदद कर सकती हूँ",
        "kya madad kar sakti hoon" to "क्या मदद कर सकती हूँ",
        "shukriya alvida" to "शुक्रिया, अलविदा"
    )

    // Single-word mappings for conversational Hindi words in Roman script
    private val WORD_MAPPINGS = mapOf(
        "bilkul" to "बिल्कुल",
        "main" to "मैं",
        "mai" to "मैं",
        "aap" to "आप",
        "aapka" to "आपका",
        "aapki" to "आपकी",
        "aapke" to "आपके",
        "karo" to "करो",
        "kare" to "करें",
        "karein" to "करें",
        "karna" to "करना",
        "khol" to "खोल",
        "kholo" to "खोलो",
        "rahi" to "रही",
        "raha" to "रहा",
        "rahe" to "रहे",
        "hoon" to "हूँ",
        "hun" to "हूँ",
        "hai" to "है",
        "hain" to "हैं",
        "hoga" to "होगा",
        "hogi" to "होगी",
        "bhejo" to "भेजो",
        "bhej" to "भेज",
        "kar" to "कर",
        "diya" to "दिया",
        "di" to "दी",
        "gaya" to "गया",
        "gayi" to "गई",
        "gaye" to "गए",
        "shukriya" to "शुक्रिया",
        "dhanyawad" to "धन्यवाद",
        "namaste" to "नमस्ते",
        "alvida" to "अलvida",
        "accha" to "अच्छा",
        "achha" to "अच्छा",
        "batao" to "बताओ",
        "bataiye" to "बताइए",
        "bolo" to "बोलो",
        "kripya" to "कृपया",
        "kijiye" to "कीजिए",
        "lagao" to "लगाओ",
        "chalao" to "चलाओ",
        "madad" to "मदद",
        "radd" to "रद्द",
        "shuru" to "शुरू",
        "band" to "बंद",
        "samay" to "समय",
        "subah" to "सुबह",
        "dopahar" to "दोपहर",
        "shaam" to "शाम",
        "raat" to "रात",
        "kal" to "कल",
        "aaj" to "आज",
        "ab" to "अब",
        "kya" to "क्या",
        "kyun" to "क्यों",
        "kaise" to "कैसे",
        "kahan" to "कहाँ",
        "kab" to "कब",
        "kaun" to "कौन",
        "mera" to "मेरा",
        "meri" to "मेरी",
        "mere" to "मेरे",
        "hum" to "हम",
        "nahi" to "नहीं",
        "nahin" to "नहीं",
        "haan" to "हाँ",
        "ha" to "हाँ",
        "bhi" to "भी",
        "aur" to "और",
        "lekin" to "लेकिन",
        "yeh" to "यह",
        "ye" to "यह",
        "woh" to "वह",
        "wo" to "वह",
        "ko" to "को",
        "se" to "से",
        "par" to "पर",
        "pe" to "पर",
        "mein" to "में",
        "ka" to "का",
        "ki" to "की",
        "ke" to "के"
    )

    // English technical terms & common loanwords that must be preserved
    private val PRESERVED_ENGLISH_WORDS = setOf(
        "whatsapp", "youtube", "alarm", "timer", "torch", "flashlight",
        "call", "phone", "message", "settings", "wifi", "bluetooth", "google",
        "camera", "notes", "contact", "dial", "open", "send", "search",
        "play", "stop", "volume", "music", "video", "battery", "akriti",
        "app", "offline", "online", "mode", "screen", "link", "chat"
    )

    /**
     * Checks if the text has Devanagari characters.
     */
    fun containsHindiScript(text: String): Boolean {
        return text.any { it in '\u0900'..'\u097F' }
    }

    /**
     * Checks if Latin text is predominantly Hinglish conversational speech.
     */
    fun isHinglish(text: String): Boolean {
        if (containsHindiScript(text)) return false
        val words = text.lowercase(Locale.ROOT)
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.isNotBlank() }

        if (words.isEmpty()) return false

        val matchingCount = words.count { it in HINGLISH_MARKERS }
        return matchingCount >= 2 || (words.size in 1..3 && matchingCount >= 1)
    }

    /**
     * Normalizes text for optimal TTS:
     * - If Devanagari Hindi, returns as-is.
     * - If Hinglish, transliterates Hindi conversational parts to Devanagari while
     *   preserving English loanwords, and routes to Hindi neural voice.
     * - If pure English, returns as-is for English neural voice.
     */
    fun normalizeForSpeech(input: String): Pair<String, String> {
        // Pair(normalizedText, targetLanguageCode)
        if (containsHindiScript(input)) {
            return Pair(input, "hi-IN")
        }

        if (isHinglish(input)) {
            var transformed = input

            // 1. Replace multi-word conversational phrases first
            for ((phrase, devanagari) in PHRASE_MAPPINGS) {
                val regex = Regex("(?i)\\b" + Regex.escape(phrase) + "\\b")
                transformed = transformed.replace(regex, devanagari)
            }

            // 2. Replace single conversational words, skipping preserved English words
            for ((word, devanagari) in WORD_MAPPINGS) {
                if (word in PRESERVED_ENGLISH_WORDS) continue
                val regex = Regex("(?i)\\b" + Regex.escape(word) + "\\b")
                transformed = transformed.replace(regex, devanagari)
            }

            return Pair(transformed, "hi-IN")
        }

        return Pair(input, "en-IN")
    }
}
