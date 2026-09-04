package com.example.domain.intent

import com.example.data.model.ActionType
import com.example.data.model.IntentCommand
import java.util.Locale

class IntentRouter {

    /**
     * Resolves an intent command. If Gemini already supplied a structured intent,
     * it validates and sanitizes it. If not, it runs rule-based checks as fallback.
     */
    fun resolveIntent(userQuery: String, candidateIntent: IntentCommand?): IntentCommand {
        if (candidateIntent != null && candidateIntent.action != ActionType.NONE) {
            return candidateIntent
        }

        val query = userQuery.lowercase(Locale.ROOT).trim()

        return when {
            // Flashlight / Torch
            query.contains("torch") || query.contains("flashlight") -> {
                val state = when {
                    query.contains("off") || query.contains("band") || query.contains("bujhao") -> "off"
                    query.contains("on") || query.contains("jalao") || query.contains("chalao") || query.contains("start") -> "on"
                    else -> "toggle"
                }
                IntentCommand(ActionType.TOGGLE_FLASHLIGHT, target = state, rawQuery = userQuery)
            }

            // Voice Notes - Take Note
            query.startsWith("note down") || query.startsWith("note karo") || query.startsWith("take a note") ||
            query.startsWith("write down") || query.contains("ek note banao") || query.contains("yaad rakhna") -> {
                val cleanContent = userQuery
                    .replace(Regex("(?i)^(note down|note karo|take a note|write down|ek note banao|yaad rakhna)\\s*(ki|that|:)?\\s*"), "")
                    .trim()
                IntentCommand(ActionType.TAKE_NOTE, target = cleanContent, rawQuery = userQuery)
            }

            // Voice Notes - Show Notes
            query.contains("mere notes") || query.contains("show notes") || query.contains("open notes") ||
            query.contains("saved notes") || query.contains("notes dikhao") || query == "notes" -> {
                IntentCommand(ActionType.SHOW_NOTES, rawQuery = userQuery)
            }

            // Direct URLs
            query.startsWith("http://") || query.startsWith("https://") || query.startsWith("www.") ||
            (query.startsWith("open ") && (query.contains(".com") || query.contains(".org") || query.contains(".in") || query.contains(".net"))) -> {
                val url = query.removePrefix("open ").trim()
                IntentCommand(ActionType.LAUNCH_URL, target = url, rawQuery = userQuery)
            }

            // Specific Settings
            query.contains("wifi") || query.contains("wi-fi") -> {
                IntentCommand(ActionType.OPEN_SETTINGS, target = "wifi", rawQuery = userQuery)
            }
            query.contains("bluetooth") -> {
                IntentCommand(ActionType.OPEN_SETTINGS, target = "bluetooth", rawQuery = userQuery)
            }
            query.contains("display setting") || query.contains("brightness") -> {
                IntentCommand(ActionType.OPEN_SETTINGS, target = "display", rawQuery = userQuery)
            }
            query.contains("sound setting") || query.contains("volume setting") -> {
                IntentCommand(ActionType.OPEN_SETTINGS, target = "sound", rawQuery = userQuery)
            }
            query.contains("settings kholo") || query.contains("open settings") || query.contains("phone settings") || query == "settings" -> {
                IntentCommand(ActionType.OPEN_SETTINGS, target = "main", rawQuery = userQuery)
            }

            // Alarms
            query.contains("alarm") -> {
                val timeExtract = extractTime(query)
                IntentCommand(ActionType.SET_ALARM, target = timeExtract, rawQuery = userQuery)
            }

            // Timers
            query.contains("timer") || query.contains("countdown") -> {
                val seconds = extractSeconds(query)
                IntentCommand(ActionType.SET_TIMER, target = seconds.toString(), rawQuery = userQuery)
            }

            // Reminders / Calendar
            query.contains("reminder") || query.contains("remind me") || query.contains("yaad dilana") -> {
                val task = query.replace("reminder", "").replace("remind me to", "").replace("yaad dilana", "").trim()
                IntentCommand(ActionType.SET_REMINDER, target = task.ifBlank { "Reminder" }, rawQuery = userQuery)
            }

            // Phone Dial / Direct Call to Contact
            query.startsWith("call ") || query.startsWith("dial ") || query.contains("call karo") ||
            query.contains("phone lagao") || query.contains("phone karo") || query.contains("ko call") || query.contains("ko phone") -> {
                val digitsOnly = query.filter { it.isDigit() || it == '+' }
                val target = if (digitsOnly.length >= 7) {
                    digitsOnly
                } else {
                    extractContactName(query) ?: digitsOnly.ifBlank { null }
                }
                IntentCommand(ActionType.DIAL_PHONE, target = target, rawQuery = userQuery)
            }

            // Messaging / SMS
            query.startsWith("message ") || query.startsWith("sms ") || query.contains("message bhejo") || query.contains("sms karo") -> {
                val digitsOnly = query.filter { it.isDigit() || it == '+' }
                val target = if (digitsOnly.length >= 7) {
                    digitsOnly
                } else {
                    extractContactName(query) ?: digitsOnly.ifBlank { null }
                }
                IntentCommand(ActionType.SEND_MESSAGE, target = target, rawQuery = userQuery)
            }

            // Battery checks
            query.contains("battery") || query.contains("charge") || query.contains("kitna charge") -> {
                IntentCommand(ActionType.BATTERY_INFO, rawQuery = userQuery)
            }

            // Date and time
            query.contains("time kya") || query.contains("kya time") || query.contains("current time") ||
            query.contains("aaj ki date") || query.contains("what time") || query.contains("aaj kaun sa din") ||
            query.contains("aaj ki tarikh") || query.contains("what is today") -> {
                IntentCommand(ActionType.DATE_TIME, rawQuery = userQuery)
            }

            // Device information
            query.contains("device info") || query.contains("phone model") || query.contains("android version") ||
            query.contains("phone info") || query.contains("kon sa phone hai") -> {
                IntentCommand(ActionType.DEVICE_INFO, rawQuery = userQuery)
            }

            // Web search
            query.startsWith("search ") || query.startsWith("google ") || query.contains("search karo") -> {
                val searchTerm = query
                    .removePrefix("search ")
                    .removePrefix("google ")
                    .replace("search karo", "")
                    .replace("google par", "")
                    .trim()
                IntentCommand(ActionType.SEARCH_WEB, target = searchTerm, rawQuery = userQuery)
            }

            // App opening patterns
            query.startsWith("open ") || query.contains("kholo") || query.contains("khol do") || query.contains("open karo") -> {
                val target = extractAppTarget(query)
                IntentCommand(ActionType.OPEN_APP, target = target, rawQuery = userQuery)
            }

            else -> IntentCommand(ActionType.NONE, rawQuery = userQuery)
        }
    }

    private fun extractAppTarget(query: String): String {
        return query
            .replace("open ", "")
            .replace("kholo", "")
            .replace("khol do", "")
            .replace("open karo", "")
            .replace("akriti", "")
            .replace("please", "")
            .replace("app", "")
            .trim()
            .ifBlank { "app" }
    }

    private fun extractTime(query: String): String {
        // Look for digit patterns like "6", "6:30", "7 am", "8 baje"
        val timeRegex = Regex("(\\d{1,2})(:\\d{2})?\\s*(am|pm|baje)?")
        val match = timeRegex.find(query)
        return match?.value?.trim() ?: "07:00"
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

    fun isLocalFastPathAction(action: ActionType): Boolean {
        return when (action) {
            ActionType.TOGGLE_FLASHLIGHT,
            ActionType.BATTERY_INFO,
            ActionType.DATE_TIME,
            ActionType.DEVICE_INFO,
            ActionType.TAKE_NOTE,
            ActionType.SHOW_NOTES,
            ActionType.OPEN_SETTINGS,
            ActionType.SET_TIMER,
            ActionType.SET_ALARM,
            ActionType.OPEN_APP,
            ActionType.DIAL_PHONE -> true
            else -> false
        }
    }

    private fun extractContactName(query: String): String? {
        // e.g. "Ansh ko call karo", "Mom ko phone lagao"
        val koCallRegex = Regex("(?i)^(.+?)\\s+ko\\s+(?:call|phone)\\s*(?:karo|lagao|lagana)?$")
        koCallRegex.find(query)?.let {
            val name = it.groupValues[1].trim()
            if (name.isNotBlank()) return name
        }

        // e.g. "call karo Ansh ko"
        val callKaroRegex = Regex("(?i)^(?:call|phone)\\s*(?:karo|lagao)?\\s+(.+?)(?:\\s+ko)?$")
        callKaroRegex.find(query)?.let {
            val name = it.groupValues[1].trim().removeSuffix(" ko")
            if (name.isNotBlank()) return name
        }

        // e.g. "call Ansh", "dial Ansh"
        if (query.startsWith("call ", ignoreCase = true)) {
            val name = query.substring(5).trim()
            if (name.isNotBlank()) return name
        }
        if (query.startsWith("dial ", ignoreCase = true)) {
            val name = query.substring(5).trim()
            if (name.isNotBlank()) return name
        }

        return null
    }
}
