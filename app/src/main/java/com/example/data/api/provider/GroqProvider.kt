package com.example.data.api.provider

import android.util.Log
import com.example.data.model.AiModels
import com.example.data.model.AiProviderType
import com.example.data.model.ChatMessage
import com.example.data.model.IntentCommand
import com.example.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GroqProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.GROQ

    companion object {
        private const val TAG = "GroqProvider"
        private const val CHAT_URL = "https://api.groq.com/openai/v1/chat/completions"
    }

    override suspend fun generateResponse(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        model: String,
        apiKey: String,
        imageBase64: String?,
        imageMimeType: String
    ): Result<Pair<String, IntentCommand?>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Groq API key is not configured. Please set your key in Settings.")
            )
        }

        try {
            val rootJson = JSONObject()
            val effectiveModel = AiModels.normalizeModel(AiProviderType.GROQ, model)
            rootJson.put("model", effectiveModel)

            val messagesArray = JSONArray()

            // System prompt
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", AiProviderUtils.SYSTEM_INSTRUCTION_TEXT)
            })

            // Context history
            val recent = conversationHistory.takeLast(8)
            for (msg in recent) {
                if (msg.isError) continue
                messagesArray.put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.text)
                })
            }

            // Current prompt
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput.ifBlank { "Hello Akriti" })
            })

            rootJson.put("messages", messagesArray)
            rootJson.put("temperature", 0.7)
            rootJson.put("max_tokens", 500)

            // Strict JSON output
            rootJson.put("response_format", JSONObject().apply {
                put("type", "json_object")
            })

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(CHAT_URL)
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .header("User-Agent", "Akriti-Android-Assistant")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, responseBody)
                Log.w(TAG, "Groq API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(ProviderException(response.code, errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(IOException("No response generated from Groq."))
            }

            val choice = choices.getJSONObject(0)
            val messageObj = choice.optJSONObject("message")
            val rawText = messageObj?.optString("content")?.trim().orEmpty()

            val (replyText, intent) = AiProviderUtils.parseReplyAndIntent(rawText, userInput)
            Result.success(Pair(replyText, intent))
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Groq request: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")

            when (code) {
                400 -> "Invalid Groq request: ${message ?: "Check parameters"}"
                401, 403 -> "Groq authentication failed: Check your Groq API key."
                429 -> "Groq rate limit or quota exceeded (HTTP 429)."
                500, 503 -> "Groq service temporarily unavailable (HTTP $code)."
                else -> message ?: "Groq returned error code $code"
            }
        } catch (e: Exception) {
            when (code) {
                429 -> "Groq rate limit or quota exceeded."
                401, 403 -> "Groq authentication failed."
                else -> "Groq request failed with HTTP $code"
            }
        }
    }
}
