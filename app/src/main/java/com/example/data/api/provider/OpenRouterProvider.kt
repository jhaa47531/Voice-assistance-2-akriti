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

class OpenRouterProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()
) : AiProvider {

    override val providerType: AiProviderType = AiProviderType.OPENROUTER

    companion object {
        private const val TAG = "OpenRouterProvider"
        private const val CHAT_URL = "https://openrouter.ai/api/v1/chat/completions"
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
                IllegalStateException("OpenRouter API key is not configured. Please set your key in Settings.")
            )
        }

        try {
            val rootJson = JSONObject()
            rootJson.put("model", model.ifBlank { "meta-llama/llama-3.3-70b-instruct" })

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

            // Current user message
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", userInput.ifBlank { "Hello Akriti" })
            })

            rootJson.put("messages", messagesArray)
            rootJson.put("temperature", 0.7)
            rootJson.put("max_tokens", 500)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(CHAT_URL)
                .post(requestBody)
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://akriti.ai")
                .header("X-Title", "Akriti Voice Assistant")
                .header("User-Agent", "Akriti-Android-Assistant")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, responseBody)
                Log.w(TAG, "OpenRouter API error (HTTP ${response.code}): $errorMsg")
                return@withContext Result.failure(ProviderException(response.code, errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val choices = jsonResponse.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return@withContext Result.failure(IOException("No response generated from OpenRouter."))
            }

            val choice = choices.getJSONObject(0)
            val messageObj = choice.optJSONObject("message")
            val rawText = messageObj?.optString("content")?.trim().orEmpty()

            val (replyText, intent) = AiProviderUtils.parseReplyAndIntent(rawText, userInput)
            Result.success(Pair(replyText, intent))
        } catch (e: Exception) {
            Log.w(TAG, "Exception during OpenRouter request: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(code: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")

            when (code) {
                400 -> "Invalid OpenRouter request: ${message ?: "Check parameters"}"
                401, 403 -> "OpenRouter authentication failed: Check your OpenRouter API key."
                429 -> "OpenRouter rate limit or credits exhausted (HTTP 429)."
                500, 502, 503 -> "OpenRouter upstream provider unavailable (HTTP $code)."
                else -> message ?: "OpenRouter returned error code $code"
            }
        } catch (e: Exception) {
            when (code) {
                429 -> "OpenRouter rate limit or credit quota reached."
                401, 403 -> "OpenRouter authentication failed."
                else -> "OpenRouter request failed with HTTP $code"
            }
        }
    }
}
