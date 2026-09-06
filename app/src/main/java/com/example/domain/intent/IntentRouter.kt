package com.example.domain.intent

import com.example.data.model.ActionType
import com.example.data.model.IntentCommand
import java.util.Locale

class IntentRouter {

    /**
     * Resolves user query into an actionable IntentCommand.
     * Evaluates local device/Android intents first so that simple commands
     * do not unnecessarily hit external AI APIs (Gemini/Groq/OpenRouter).
     */
    fun resolveIntent(userQuery: String, candidateIntent: IntentCommand?): IntentCommand {
        if (candidateIntent != null && candidateIntent.action != ActionType.NONE) {
            return candidateIntent
        }

        val query = userQuery.lowercase(Locale.ROOT).trim()

        return when {
            // 1. WhatsApp Commands
            isWhatsAppQuery(query) -> parseWhatsAppCommand(userQuery, query)

            // 2. YouTube Commands
            isYouTubeQuery(query) -> parseYouTubeCommand(userQuery, query)

            // 3. Calling Commands
            isCallingQuery(query) -> parseCallingCommand(userQuery, query)

            // 4. Alarm & Reminders
            isAlarmQuery(query) -> parseAlarmCommand(userQuery, query)
            isTimerQuery(query) -> parseTimerCommand(userQuery, query)
            isReminderQuery(query) -> parseReminderCommand(userQuery, query)

            // 5. Flashlight / Torch
            query.contains("torch") || query.contains("flashlight") -> {
                val state = when {
                    query.contains("off") || query.contains("band") || query.contains("bujhao") -> "off"
                    query.contains("on") || query.contains("jalao") || query.contains("chalao") || query.contains("start") -> "on"
                    else -> "toggle"
                }
                IntentCommand(ActionType.TOGGLE_FLASHLIGHT, target = state, rawQuery = userQuery)
            }

            // 6. Voice Notes
            query.startsWith("note down") || query.startsWith("note karo") || query.startsWith("take a note") ||
            query.startsWith("write down") || query.contains("ek note banao") || query.contains("yaad rakhna") -> {
                val cleanContent = userQuery
                    .replace(Regex("(?i)^(note down|note karo|take a note|write down|ek note banao|yaad rakhna)\\s*(ki|that|:)?\\s*"), "")
                    .trim()
                IntentCommand(ActionType.TAKE_NOTE, target = cleanContent, rawQuery = userQuery)
            }
            query.contains("mere notes") || query.contains("show notes") || query.contains("open notes") ||
            query.contains("saved notes") || query.contains("notes dikhao") || query == "notes" -> {
                IntentCommand(ActionType.SHOW_NOTES, rawQuery = userQuery)
            }

            // 7. Android Settings
            isSettingsQuery(query) -> parseSettingsCommand(userQuery, query)

            // 8. Web Search (Google / Web / Internet)
            isWebSearchQuery(query) -> parseWebSearchCommand(userQuery, query)

            // 9. Direct URLs
            query.startsWith("http://") || query.startsWith("https://") || query.startsWith("www.") ||
            (query.startsWith("open ") && (query.contains(".com") || query.contains(".org") || query.contains(".in") || query.contains(".net"))) -> {
                val url = query.removePrefix("open ").trim()
                IntentCommand(ActionType.LAUNCH_URL, target = url, rawQuery = userQuery)
            }

            // 10. SMS Messaging
            query.startsWith("message ") || query.startsWith("sms ") || query.contains("sms bhejo") || query.contains("sms karo") -> {
                val digitsOnly = query.filter { it.isDigit() || it == '+' }
                val target = if (digitsOnly.length >= 7) digitsOnly else extractContactName(query) ?: digitsOnly.ifBlank { null }
                IntentCommand(ActionType.SEND_MESSAGE, target = target, rawQuery = userQuery)
            }

            // 11. Battery Info
            query.contains("battery") || query.contains("charge") || query.contains("kitna charge") -> {
                IntentCommand(ActionType.BATTERY_INFO, rawQuery = userQuery)
            }

            // 12. Date and Time
            query.contains("time kya") || query.contains("kya time") || query.contains("current time") ||
            query.contains("aaj ki date") || query.contains("what time") || query.contains("aaj kaun sa din") ||
            query.contains("aaj ki tarikh") || query.contains("what is today") -> {
                IntentCommand(ActionType.DATE_TIME, rawQuery = userQuery)
            }

            // 13. Device Information
            query.contains("device info") || query.contains("phone model") || query.contains("android version") ||
            query.contains("phone info") || query.contains("kon sa phone hai") -> {
                IntentCommand(ActionType.DEVICE_INFO, rawQuery = userQuery)
            }

            // 14. App Launching (e.g. "Instagram kholo", "Chrome kholo", "Calculator kholo")
            query.startsWith("open ") || query.contains("kholo") || query.contains("khol do") || query.contains("open karo") || query.contains("chalao") -> {
                val target = extractAppTarget(query)
                IntentCommand(ActionType.OPEN_APP, target = target, rawQuery = userQuery)
            }

            // 15. General queries fall through to AI
            else -> IntentCommand(ActionType.NONE, rawQuery = userQuery)
        }
    }

    // --- WhatsApp Parsing ---
    private fun isWhatsAppQuery(q: String): Boolean {
        return q.contains("whatsapp") || q.contains("what's app") || q.contains("whats app")
    }

    private fun parseWhatsAppCommand(rawQuery: String, query: String): IntentCommand {
        // Check if user simply wants to open WhatsApp
        val openPatterns = listOf(
            "whatsapp kholo", "open whatsapp", "whatsapp open", "whatsapp open karo",
            "whatsapp chalao", "whatsapp khol do", "whatsapp start karo", "kholo whatsapp"
        )
        if (openPatterns.any { query == it || query == "akriti $it" } || query.trim() == "whatsapp") {
            return IntentCommand(ActionType.WHATSAPP_OPEN, rawQuery = rawQuery)
        }

        // WhatsApp message commands:
        // e.g. "Ansh ko WhatsApp par message bhejo: Kal college aana"
        // e.g. "Ansh ko WhatsApp par bolo kal college aana"
        // e.g. "WhatsApp par Ansh ko message bhejo: Kal aana"
        var recipient: String? = null
        var message: String? = null

        // Check if there is an explicit colon separating contact and message
        if (rawQuery.contains(":")) {
            val parts = rawQuery.split(":", limit = 2)
            val header = parts[0].lowercase(Locale.ROOT)
            message = parts[1].trim()
            val rawHeader = parts[0]
            val lowerRecipient = extractContactNameFromWhatsAppHeader(header)
            if (lowerRecipient != null) {
                recipient = preserveCaseFromRaw(rawHeader, lowerRecipient)
            }
        } else {
            // Regex patterns without colon
            // Pattern: "<name> ko whatsapp (par|pe)? (message bhejo|bolo|likho) <message>"
            val pattern1 = Regex("(?i)^(.+?)\\s+ko\\s+whatsapp\\s*(?:par|pe)?\\s*(?:message\\s+bhejo|message\\s+karo|bolo|likho|send\\s+karo)\\s*(.*)$")
            val match1 = pattern1.find(query)
            if (match1 != null) {
                val lowerRec = match1.groupValues[1].trim()
                recipient = preserveCaseFromRaw(rawQuery, lowerRec)
                val extractedMsg = match1.groupValues[2].trim()
                if (extractedMsg.isNotBlank()) {
                    message = extractedMsg
                }
            } else {
                // Pattern: "whatsapp (par|pe)? (.+?) ko (message bhejo|bolo) (.*)"
                val pattern2 = Regex("(?i)^whatsapp\\s*(?:par|pe)?\\s*(.+?)\\s+ko\\s*(?:message\\s+bhejo|bolo|likho)?\\s*(.*)$")
                val match2 = pattern2.find(query)
                if (match2 != null) {
                    val lowerRec = match2.groupValues[1].trim()
                    recipient = preserveCaseFromRaw(rawQuery, lowerRec)
                    val extractedMsg = match2.groupValues[2].trim()
                    if (extractedMsg.isNotBlank()) {
                        message = extractedMsg
                    }
                } else {
                    // Pattern: "send whatsapp message to <name> (saying)? (.*)"
                    val pattern3 = Regex("(?i)^send\\s+whatsapp\\s+(?:message\\s+)?to\\s+(.+?)(?:\\s+saying\\s+|:\\s*|\\s+)(.*)$")
                    val match3 = pattern3.find(query)
                    if (match3 != null) {
                        val lowerRec = match3.groupValues[1].trim()
                        recipient = preserveCaseFromRaw(rawQuery, lowerRec)
                        val extractedMsg = match3.groupValues[2].trim()
                        if (extractedMsg.isNotBlank()) {
                            message = extractedMsg
                        }
                    }
                }
            }
        }

        // Fallback recipient clean up
        recipient = recipient?.removePrefix("akriti")?.removePrefix("please")?.trim()
        if (recipient.isNullOrBlank()) {
            recipient = extractContactName(query, rawQuery)
        }

        val params = mutableMapOf<String, String>()
        if (!recipient.isNullOrBlank()) {
            params["recipient"] = recipient
        }
        if (!message.isNullOrBlank()) {
            params["message"] = message
        }

        return IntentCommand(
            action = ActionType.WHATSAPP_MESSAGE,
            target = recipient,
            rawQuery = rawQuery,
            parameters = params
        )
    }

    private fun extractContactNameFromWhatsAppHeader(header: String): String? {
        val cleaned = header
            .replace("akriti", "")
            .replace("please", "")
            .replace("whatsapp", "")
            .replace("par", "")
            .replace("pe", "")
            .replace("message", "")
            .replace("bhejo", "")
            .replace("karo", "")
            .replace("send", "")
            .replace("ko", "")
            .replace("to", "")
            .trim()
        return cleaned.ifBlank { null }
    }

    // --- YouTube Parsing ---
    private fun isYouTubeQuery(q: String): Boolean {
        return q.contains("youtube") || q.contains("you tube") || Regex("(?i)\\byt\\b").containsMatchIn(q)
    }

    private fun parseYouTubeCommand(rawQuery: String, query: String): IntentCommand {
        val openPatterns = listOf(
            "youtube kholo", "open youtube", "youtube open", "youtube open karo",
            "youtube chalao", "youtube khol do", "open yt", "yt kholo"
        )
        if (openPatterns.any { query == it || query == "akriti $it" } || query.trim() == "youtube") {
            return IntentCommand(ActionType.YOUTUBE_OPEN, rawQuery = rawQuery)
        }

        // Extract search query
        var searchTerm = query
            .replace("search on youtube", "")
            .replace("search in youtube", "")
            .replace("on youtube", "")
            .replace("in youtube", "")
            .replace("youtube par search karo", "")
            .replace("youtube pe search karo", "")
            .replace("youtube par search", "")
            .replace("youtube pe search", "")
            .replace("youtube search karo", "")
            .replace("youtube search", "")
            .replace("youtube par chalao", "")
            .replace("youtube pe chalao", "")
            .replace("youtube par", "")
            .replace("youtube pe", "")
            .replace("youtube", "")
            .replace("search karo", "")
            .replace("chalao", "")
            .replace("play", "")
            .replace("kholo", "")
            .replace("akriti", "")
            .replace("please", "")
            .trim()

        if (searchTerm.isBlank()) {
            return IntentCommand(ActionType.YOUTUBE_OPEN, rawQuery = rawQuery)
        }

        return IntentCommand(
            action = ActionType.YOUTUBE_SEARCH,
            target = searchTerm,
            rawQuery = rawQuery
        )
    }

    // --- Calling Parsing ---
    private fun isCallingQuery(q: String): Boolean {
        // Exclude WhatsApp calls from standard phone dialer
        if (q.contains("whatsapp")) return false

        return q.startsWith("call ") || q.startsWith("dial ") ||
               q.contains("call karo") || q.contains("phone lagao") ||
               q.contains("phone karo") || q.contains("ko call") ||
               q.contains("ko phone") || q.endsWith(" call")
    }

    private fun parseCallingCommand(rawQuery: String, query: String): IntentCommand {
        val digitsOnly = query.filter { it.isDigit() || it == '+' }
        val target = if (digitsOnly.length >= 7) {
            digitsOnly
        } else {
            extractContactName(query, rawQuery) ?: digitsOnly.ifBlank { null }
        }

        return IntentCommand(ActionType.CALL_PHONE, target = target, rawQuery = rawQuery)
    }

    // --- Alarm Parsing ---
    private fun isAlarmQuery(q: String): Boolean {
        return q.contains("alarm")
    }

    private fun parseAlarmCommand(rawQuery: String, query: String): IntentCommand {
        val (hour, minute) = parseAlarmTime(query)
        val timeFormatted = String.format("%02d:%02d", hour, minute)
        val params = mapOf(
            "hour" to hour.toString(),
            "minute" to minute.toString()
        )
        return IntentCommand(ActionType.SET_ALARM, target = timeFormatted, rawQuery = rawQuery, parameters = params)
    }

    private fun parseAlarmTime(query: String): Pair<Int, Int> {
        val isPm = query.contains("pm") || query.contains("shaam") || query.contains("dopahar") || query.contains("raat") || query.contains("evening") || query.contains("night")
        val isAm = query.contains("am") || query.contains("subah") || query.contains("morning")

        // 1. Check HH:MM format e.g. "6:30", "07:15"
        val colonRegex = Regex("(\\d{1,2}):(\\d{2})")
        colonRegex.find(query)?.let { match ->
            var h = match.groupValues[1].toIntOrNull() ?: 7
            val m = match.groupValues[2].toIntOrNull() ?: 0
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            return Pair(h, m)
        }

        // 2. Check "HH baje" or "HH:MM baje" or "HH am/pm" e.g. "7 baje", "8:30 baje", "6 am"
        val bajeRegex = Regex("(\\d{1,2})\\s*(?:baje|am|pm)?")
        bajeRegex.find(query)?.let { match ->
            var h = match.groupValues[1].toIntOrNull() ?: 7
            if (isPm && h < 12) h += 12
            if (isAm && h == 12) h = 0
            return Pair(h, 0)
        }

        return Pair(7, 0)
    }

    // --- Timer Parsing ---
    private fun isTimerQuery(q: String): Boolean {
        return q.contains("timer") || q.contains("countdown")
    }

    private fun parseTimerCommand(rawQuery: String, query: String): IntentCommand {
        val seconds = extractSeconds(query)
        return IntentCommand(ActionType.SET_TIMER, target = seconds.toString(), rawQuery = rawQuery)
    }

    // --- Reminder Parsing ---
    private fun isReminderQuery(q: String): Boolean {
        return q.contains("reminder") || q.contains("remind me") || q.contains("yaad dilana") || q.contains("yaad dila do")
    }

    private fun parseReminderCommand(rawQuery: String, query: String): IntentCommand {
        val task = query
            .replace("reminder lagao", "")
            .replace("reminder", "")
            .replace("remind me to", "")
            .replace("remind me", "")
            .replace("yaad dilana", "")
            .replace("yaad dila do", "")
            .replace("mujhe", "")
            .replace("ki", "")
            .replace("akriti", "")
            .trim()
        return IntentCommand(ActionType.SET_REMINDER, target = task.ifBlank { "Reminder" }, rawQuery = rawQuery)
    }

    // --- Settings Parsing ---
    private fun isSettingsQuery(q: String): Boolean {
        return q.contains("wifi") || q.contains("wi-fi") || q.contains("bluetooth") ||
               q.contains("display setting") || q.contains("brightness") ||
               q.contains("sound setting") || q.contains("volume setting") ||
               q.contains("battery setting") || q.contains("apps setting") ||
               q.contains("date time setting") || q.contains("airplane mode") ||
               q.contains("settings kholo") || q.contains("open settings") ||
               q.contains("phone settings") || q == "settings" || q == "setting"
    }

    private fun parseSettingsCommand(rawQuery: String, query: String): IntentCommand {
        val target = when {
            query.contains("wifi") || query.contains("wi-fi") || query.contains("internet") -> "wifi"
            query.contains("bluetooth") -> "bluetooth"
            query.contains("display") || query.contains("brightness") || query.contains("screen") -> "display"
            query.contains("sound") || query.contains("volume") || query.contains("audio") -> "sound"
            query.contains("battery") -> "battery"
            query.contains("apps") || query.contains("application") -> "apps"
            query.contains("datetime") || query.contains("date") || query.contains("time") -> "datetime"
            query.contains("airplane") || query.contains("flight") -> "airplane"
            else -> "main"
        }
        return IntentCommand(ActionType.OPEN_SETTINGS, target = target, rawQuery = rawQuery)
    }

    // --- Web Search Parsing ---
    private fun isWebSearchQuery(q: String): Boolean {
        return q.startsWith("google ") || q.startsWith("search ") ||
               q.contains("google par") || q.contains("google pe") ||
               q.contains("web par") || q.contains("web pe") ||
               q.contains("internet par") || q.contains("internet pe") ||
               q.contains("search karo")
    }

    private fun parseWebSearchCommand(rawQuery: String, query: String): IntentCommand {
        val searchTerm = query
            .replace("search on google", "")
            .replace("google search", "")
            .replace("google par search karo", "")
            .replace("google pe search karo", "")
            .replace("google par", "")
            .replace("google pe", "")
            .replace("web par search karo", "")
            .replace("web pe search karo", "")
            .replace("web par", "")
            .replace("web pe", "")
            .replace("internet par search karo", "")
            .replace("internet pe search karo", "")
            .replace("internet par", "")
            .replace("internet pe", "")
            .removePrefix("search ")
            .removePrefix("google ")
            .replace("search karo", "")
            .replace("akriti", "")
            .replace("please", "")
            .trim()

        return IntentCommand(ActionType.SEARCH_WEB, target = searchTerm, rawQuery = rawQuery)
    }

    // --- Helper Utilities ---
    private fun extractAppTarget(query: String): String {
        return query
            .replace("open ", "")
            .replace("kholo", "")
            .replace("khol do", "")
            .replace("open karo", "")
            .replace("chalao", "")
            .replace("akriti", "")
            .replace("please", "")
            .replace("app", "")
            .trim()
            .ifBlank { "app" }
    }

    private fun extractSeconds(query: String): Int {
        val numberRegex = Regex("(\\d+)")
        val match = numberRegex.find(query)
        val num = match?.value?.toIntOrNull() ?: 5
        return if (query.contains("second") || query.contains("sec")) {
            num
        } else {
            num * 60 // Default assume minutes
        }
    }

    fun isConversationEndPhrase(rawText: String): Boolean {
        val q = rawText.lowercase(Locale.ROOT).trim()
        val stopPhrases = listOf(
            "bye", "alvida", "goodbye", "stop", "shukriya", "thank you", "thanks",
            "bas", "bas itna hi", "band karo", "ruk jao", "exit", "quit", "chalo bye",
            "alvida akriti", "bye akriti", "thank you akriti"
        )
        return stopPhrases.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    /**
     * Checks if user affirmative response (e.g. for confirming call/message).
     */
    fun isAffirmativeResponse(text: String): Boolean {
        val q = text.lowercase(Locale.ROOT).trim()
        val affirmativeList = listOf(
            "haan", "ha", "yes", "yeah", "yep", "bhejo", "send", "kar do", "kardo",
            "call", "call karo", "lagao", "sure", "ok", "okay", "theek hai", "bilkul"
        )
        return affirmativeList.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    /**
     * Checks if user negative response (e.g. for cancelling call/message).
     */
    fun isNegativeResponse(text: String): Boolean {
        val q = text.lowercase(Locale.ROOT).trim()
        val negativeList = listOf(
            "nahi", "na", "no", "nope", "cancel", "radd", "radd karo",
            "mat karo", "don't", "stop", "rehndo", "rehne do"
        )
        return negativeList.any { q == it || q.startsWith("$it ") || q.endsWith(" $it") }
    }

    /**
     * Distinguishes local fast-path actions from general AI questions.
     * All local device commands return true and are executed without hitting AI APIs!
     */
    fun isLocalFastPathAction(action: ActionType): Boolean {
        return when (action) {
            ActionType.WHATSAPP_OPEN,
            ActionType.WHATSAPP_MESSAGE,
            ActionType.YOUTUBE_OPEN,
            ActionType.YOUTUBE_SEARCH,
            ActionType.CALL_PHONE,
            ActionType.DIAL_PHONE,
            ActionType.SEND_MESSAGE,
            ActionType.SEARCH_WEB,
            ActionType.LAUNCH_URL,
            ActionType.OPEN_APP,
            ActionType.OPEN_SETTINGS,
            ActionType.SET_ALARM,
            ActionType.SET_TIMER,
            ActionType.SET_REMINDER,
            ActionType.TOGGLE_FLASHLIGHT,
            ActionType.BATTERY_INFO,
            ActionType.DATE_TIME,
            ActionType.DEVICE_INFO,
            ActionType.TAKE_NOTE,
            ActionType.SHOW_NOTES,
            ActionType.SHARE_CONTENT -> true
            ActionType.NONE -> false
        }
    }

    fun extractContactName(query: String, rawQuery: String = query): String? {
        // e.g. "Ansh ko call karo", "Mom ko phone lagao"
        val koCallRegex = Regex("(?i)^(.+?)\\s+ko\\s+(?:call|phone)\\s*(?:karo|lagao|lagana)?$")
        koCallRegex.find(query)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotBlank()) return preserveCaseFromRaw(rawQuery, name)
        }

        // e.g. "call karo Ansh ko"
        val callKaroRegex = Regex("(?i)^(?:call|phone)\\s*(?:karo|lagao)?\\s+(.+?)(?:\\s+ko)?$")
        callKaroRegex.find(query)?.let {
            val name = it.groupValues[1].trim().removeSuffix(" ko")
            if (name.isNotBlank()) return preserveCaseFromRaw(rawQuery, name)
        }

        // e.g. "call Ansh", "dial Ansh"
        if (query.startsWith("call ", ignoreCase = true)) {
            val name = query.substring(5).trim()
            if (name.isNotBlank()) return preserveCaseFromRaw(rawQuery, name)
        }
        if (query.startsWith("dial ", ignoreCase = true)) {
            val name = query.substring(5).trim()
            if (name.isNotBlank()) return preserveCaseFromRaw(rawQuery, name)
        }

        return null
    }

    private fun preserveCaseFromRaw(rawQuery: String, lowerName: String): String {
        val index = rawQuery.indexOf(lowerName, ignoreCase = true)
        return if (index != -1 && index + lowerName.length <= rawQuery.length) {
            rawQuery.substring(index, index + lowerName.length).trim()
        } else {
            lowerName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
