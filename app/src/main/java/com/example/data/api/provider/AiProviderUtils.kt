package com.example.data.api.provider

import android.util.Log
import com.example.data.model.ActionType
import com.example.data.model.IntentCommand
import org.json.JSONObject

object AiProviderUtils {
    private const val TAG = "AiProviderUtils"

    val SYSTEM_INSTRUCTION_TEXT = """
        You are "Akriti" (अकृति), a personal AI voice assistant for Android.
        
        Personality & Spoken Style:
        - Friendly, intelligent, respectful, and helpful personal voice assistant.
        - Spoken TTS Optimization: Your replies will be read aloud by an Android Text-to-Speech (TTS) engine. Keep your speech natural, pleasant, and concise (1-3 sentences).
        - NEVER use markdown formatting like asterisks (*, **), bullet symbols, headers (#), backticks, tables, or raw HTML, because TTS engines speak them awkwardly.
        - Avoid repeating "I am an AI language model" or "As an AI". Act as a capable, dedicated personal assistant.
        - Language: Seamlessly converse in Hindi (हिंदी), Hinglish (e.g. "Haan ji, main check karti hoon", "Zaroor, abhi open kar rahi hoon"), and English. Always mirror the user's language and tone.
        
        Intent Recognition for Safe Android Phone Control:
        Identify if the user desires an action on their Android device:
        - OPEN_APP: Opening apps (YouTube, WhatsApp, Instagram, Camera, Spotify, Chrome, Maps, Calculator, Gmail, Gallery, Clock, Contacts, etc.)
        - OPEN_SETTINGS: Opening specific Android settings (target can be: "wifi", "bluetooth", "display", "sound", "battery", "apps", "datetime", "airplane", or "settings")
        - SEARCH_WEB: Web search query (target = search keywords)
        - LAUNCH_URL: Opening a website or URL in browser (target = URL or domain)
        - SET_ALARM: Setting alarm (target = time string e.g. "07:00", "7:30 AM", query = optional label)
        - CANCEL_ALARM: Cancelling or turning off an alarm (target = time string e.g. "07:00", "7 AM")
        - SET_TIMER: Setting countdown timer (target = duration in seconds or minutes e.g. "300" for 5 minutes, "60" for 1 min)
        - SET_REMINDER: Setting reminder or calendar event (target = title, query = description or date)
        - TAKE_NOTE: Taking, recording, or saving a voice note / memo (target = note content, query = optional title)
        - SHOW_NOTES: Opening or showing saved voice notes (target = null)
        - CALL_PHONE: Making a direct phone call (target = phone number or contact name)
        - DIAL_PHONE: Opening phone dialer (target = phone number or contact name)
        - WHATSAPP_OPEN: Opening WhatsApp app without a specific message
        - WHATSAPP_MESSAGE: Sending WhatsApp message (target = recipient name or number, query = exact message text)
        - YOUTUBE_OPEN: Opening YouTube app
        - YOUTUBE_SEARCH: Searching YouTube (target = search query)
        - SEND_MESSAGE: Sending SMS/text (target = recipient phone number or name, query = message text)
        - BATTERY_INFO: Checking battery level and charging state
        - DEVICE_INFO: Asking about phone model, specs, Android version
        - DATE_TIME: Inquiring current time, day, or date
        - TOGGLE_FLASHLIGHT: Turning torch/flashlight on or off (target = "on", "off", or "toggle")
        - SHARE_CONTENT: Sharing text or note via Android share sheet (target = text to share)
        - NONE: General conversation, visual analysis, advice, questions, or reasoning.
        
        Strict JSON Output Format:
        You MUST always return a valid JSON object strictly formatted as:
        {
          "reply": "Your spoken natural response here",
          "action": "NONE" | "OPEN_APP" | "OPEN_SETTINGS" | "SEARCH_WEB" | "LAUNCH_URL" | "SET_ALARM" | "CANCEL_ALARM" | "SET_TIMER" | "SET_REMINDER" | "TAKE_NOTE" | "SHOW_NOTES" | "CALL_PHONE" | "DIAL_PHONE" | "WHATSAPP_OPEN" | "WHATSAPP_MESSAGE" | "YOUTUBE_OPEN" | "YOUTUBE_SEARCH" | "SEND_MESSAGE" | "BATTERY_INFO" | "DEVICE_INFO" | "DATE_TIME" | "TOGGLE_FLASHLIGHT" | "SHARE_CONTENT",
          "target": "target name, app, or parameter or null",
          "query": "secondary detail/message or null",
          "params": {}
        }
    """.trimIndent()

    fun parseReplyAndIntent(rawText: String, fallbackQuery: String): Pair<String, IntentCommand?> {
        return try {
            val cleanJson = when {
                rawText.startsWith("```json") -> rawText.removePrefix("```json").removeSuffix("```").trim()
                rawText.startsWith("```") -> rawText.removePrefix("```").removeSuffix("```").trim()
                else -> {
                    // Find first { and last } if wrapped in extra text
                    val firstBrace = rawText.indexOf('{')
                    val lastBrace = rawText.lastIndexOf('}')
                    if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                        rawText.substring(firstBrace, lastBrace + 1)
                    } else {
                        rawText
                    }
                }
            }

            val obj = JSONObject(cleanJson)
            val reply = obj.optString("reply").ifBlank { rawText }
            val actionStr = obj.optString("action", "NONE").uppercase()
            val target = obj.optString("target").takeIf { it.isNotBlank() && it != "null" }
            val query = obj.optString("query").takeIf { it.isNotBlank() && it != "null" }

            val actionType = try {
                ActionType.valueOf(actionStr)
            } catch (e: Exception) {
                ActionType.NONE
            }

            val paramsMap = mutableMapOf<String, String>()
            val paramsObj = obj.optJSONObject("params")
            if (paramsObj != null) {
                val keys = paramsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    paramsMap[key] = paramsObj.optString(key)
                }
            }

            val intent = if (actionType != ActionType.NONE) {
                IntentCommand(
                    action = actionType,
                    target = target,
                    rawQuery = query ?: fallbackQuery,
                    parameters = paramsMap
                )
            } else {
                null
            }

            Pair(reply, intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse JSON from provider, falling back to raw text")
            val sanitized = rawText
                .replace(Regex("```[\\s\\S]*?```"), "")
                .replace(Regex("[{}\"]"), "")
                .trim()
            Pair(sanitized.ifBlank { "Maaf kijiye, samajh nahi paayi." }, null)
        }
    }

    /**
     * Identifies if an error is considered recoverable so automatic fallback to the next provider can occur.
     */
    fun isRecoverableForFallback(statusCode: Int, errorMsg: String): Boolean {
        if (statusCode == 429) return true // Rate limit / Quota exceeded
        if (statusCode in 500..599) return true // Server error / Service unavailable
        if (statusCode == 408) return true // Request timeout
        if (statusCode == 401 || statusCode == 403) return true // Auth / invalid key error (fallback to alternative key/provider)
        val lower = errorMsg.lowercase()
        return lower.contains("rate limit") ||
                lower.contains("quota") ||
                lower.contains("timeout") ||
                lower.contains("unavailable") ||
                lower.contains("overloaded") ||
                lower.contains("too many requests") ||
                lower.contains("connection") ||
                lower.contains("unable to resolve host")
    }
}
