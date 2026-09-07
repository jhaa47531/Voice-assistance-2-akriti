package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.ActionType
import com.example.data.model.CommandHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class CommandHistoryRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("akriti_command_history", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow<List<CommandHistoryItem>>(loadHistory())
    val history: StateFlow<List<CommandHistoryItem>> = _history.asStateFlow()

    private fun loadHistory(): List<CommandHistoryItem> {
        val rawJson = prefs.getString("saved_history", null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<CommandHistoryItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val actionStr = obj.optString("actionType", ActionType.NONE.name)
                val action = runCatching { ActionType.valueOf(actionStr) }.getOrDefault(ActionType.NONE)
                list.add(
                    CommandHistoryItem(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        query = obj.optString("query"),
                        actionType = action,
                        resultSummary = obj.optString("resultSummary"),
                        isSuccess = obj.optBoolean("isSuccess", true)
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveHistory(list: List<CommandHistoryItem>) {
        val trimmed = list.take(100) // Keep the most recent 100 entries
        val jsonArray = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestamp", item.timestamp)
                put("query", item.query)
                put("actionType", item.actionType.name)
                put("resultSummary", item.resultSummary)
                put("isSuccess", item.isSuccess)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_history", jsonArray.toString()).apply()
        _history.value = trimmed
    }

    fun addEntry(query: String, actionType: ActionType, resultSummary: String, isSuccess: Boolean = true): CommandHistoryItem {
        val entry = CommandHistoryItem(
            query = query,
            actionType = actionType,
            resultSummary = resultSummary,
            isSuccess = isSuccess
        )
        val current = _history.value.toMutableList()
        current.add(0, entry)
        saveHistory(current)
        return entry
    }

    fun deleteEntry(id: String) {
        val current = _history.value.filter { it.id != id }
        saveHistory(current)
    }

    fun clearHistory() {
        saveHistory(emptyList())
    }
}
