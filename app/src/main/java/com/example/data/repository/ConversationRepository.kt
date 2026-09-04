package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ActionType
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand
import com.example.data.model.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ConversationRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("akriti_conversations", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        val jsonStr = prefs.getString("chat_history", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id")
                    val roleStr = obj.optString("role", "USER")
                    val role = runCatching { MessageRole.valueOf(roleStr) }.getOrDefault(MessageRole.USER)
                    val text = obj.optString("text")
                    val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    val isError = obj.optBoolean("isError", false)

                    val intentObj = obj.optJSONObject("intent")
                    val intent = if (intentObj != null) {
                        val action = runCatching {
                            ActionType.valueOf(intentObj.optString("action", "NONE"))
                        }.getOrDefault(ActionType.NONE)
                        val target = intentObj.optString("target").takeIf { it.isNotBlank() }
                        IntentCommand(action = action, target = target)
                    } else null

                    list.add(
                        ChatMessage(
                            id = id,
                            role = role,
                            text = text,
                            timestamp = timestamp,
                            detectedIntent = intent,
                            isError = isError
                        )
                    )
                }
                _messages.value = list
            } catch (e: Exception) {
                // If corrupted, initialize fresh
                _messages.value = defaultWelcomeMessages()
            }
        } else {
            _messages.value = defaultWelcomeMessages()
        }
    }

    private fun defaultWelcomeMessages(): List<ChatMessage> {
        return listOf(
            ChatMessage(
                role = MessageRole.ASSISTANT,
                text = "नमस्ते! मैं अकृति हूँ, आपकी पर्सनल AI वॉइस असिस्टेंट। आप मुझसे हिंदी, Hinglish या English में बात कर सकते हैं। आप क्या जानना चाहते हैं?",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun addMessage(message: ChatMessage) {
        val updated = _messages.value + message
        _messages.value = updated
        persistMessages(updated)
    }

    fun clearConversation() {
        val welcome = defaultWelcomeMessages()
        _messages.value = welcome
        persistMessages(welcome)
    }

    private fun persistMessages(messages: List<ChatMessage>) {
        try {
            val array = JSONArray()
            // Keep last 40 messages to prevent excessive storage
            for (msg in messages.takeLast(40)) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.name)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("isError", msg.isError)
                    if (msg.detectedIntent != null) {
                        put("intent", JSONObject().apply {
                            put("action", msg.detectedIntent.action.name)
                            put("target", msg.detectedIntent.target ?: "")
                        })
                    }
                }
                array.put(obj)
            }
            prefs.edit().putString("chat_history", array.toString()).apply()
        } catch (e: Exception) {
            // Log or ignore persistence failures
        }
    }
}
