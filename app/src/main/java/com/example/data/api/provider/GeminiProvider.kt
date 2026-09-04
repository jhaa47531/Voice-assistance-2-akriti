package com.example.data.api.provider

import android.util.Log
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

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.GEMINI

    companion object {
        private const val TAG = "GeminiProvider"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    override suspend fun generateResponse(
        userInput: String,
        conversationHistory: List<ChatMessage>,
        model: String,
        apiKey: String,
        imageBase64: String?,
        imageMimeType: String
    ): Result<Pair<String, IntentCommand?>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Please set your key in Settings.")
            )
        }

        try {
            val url = "$BASE_URL/$model:generateContent?key=$apiKey"

            val rootJson = JSONObject()

            // System Instruction
            val systemInstructionJson = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", AiProviderUtils.SYSTEM_INSTRUCTION_TEXT)
                    })
                })
            }
            rootJson.put("system_instruction", systemInstructionJson)

            // Contents (History + Current query)
            val contentsArray = JSONArray()
            val recentMessages = conversationHistory.takeLast(8)
            for (msg in recentMessages) {
                if (msg.isError) continue
                val role = if (msg.role == MessageRole.USER) "user" else "model"
                contentsArray.put(JSONObject().apply {
                    put("role", role)
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", msg.text)
                        })
                    })
                })
            }

            // Current input with optional image part
            val currentParts = JSONArray()
            if (!imageBase64.isNullOrBlank()) {
                currentParts.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", imageMimeType)
                        put("data", imageBase64)
                    })
                })
            }
            currentParts.put(JSONObject().apply {
                put("text", userInput.ifBlank { "Describe and explain what you see in this image." })
            })

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", currentParts)
            })
            rootJson.put("contents", contentsArray)

            // Generation Config
            val generationConfig = JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 500)
                put("responseMimeType", "application/json")
            }
            rootJson.put("generationConfig", generationConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("User-Agent", "Akriti-Android-Assistant")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, responseBody)
                Log.w(TAG, "Gemini API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(ProviderException(response.code, errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(IOException("No response generated from Gemini."))
            }

            val candidate = candidates.getJSONObject(0)
            val content = candidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text")?.trim().orEmpty()

            val (replyText, intent) = AiProviderUtils.parseReplyAndIntent(rawText, userInput)
            Result.success(Pair(replyText, intent))
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Gemini request: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")

            when (code) {
                400 -> "Invalid request: ${message ?: "Check parameters"}"
                401, 403 -> "Authentication failed: Check your Gemini API key."
                429 -> "Gemini quota or rate limit reached (HTTP 429)."
                500, 503 -> "Gemini service temporarily unavailable (HTTP $code)."
                else -> message ?: "Gemini returned error code $code"
            }
        } catch (e: Exception) {
            when (code) {
                429 -> "Gemini rate limit or quota exceeded."
                401, 403 -> "Gemini authentication failed."
                else -> "Gemini request failed with HTTP $code"
            }
        }
    }
}
