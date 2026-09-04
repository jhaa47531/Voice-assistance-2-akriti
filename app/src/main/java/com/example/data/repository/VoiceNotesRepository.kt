package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.VoiceNote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class VoiceNotesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("akriti_voice_notes", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow<List<VoiceNote>>(loadNotes())
    val notes: StateFlow<List<VoiceNote>> = _notes.asStateFlow()

    private fun loadNotes(): List<VoiceNote> {
        val rawJson = prefs.getString("saved_notes", null) ?: return defaultNotes()
        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<VoiceNote>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    VoiceNote(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        content = obj.optString("content"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
            list.sortedWith(compareByDescending<VoiceNote> { it.isPinned }.thenByDescending { it.timestamp })
        } catch (e: Exception) {
            defaultNotes()
        }
    }

    private fun defaultNotes(): List<VoiceNote> {
        return listOf(
            VoiceNote(
                title = "Welcome to Akriti Notes",
                content = "Aap 'Note karo: Meeting at 4 PM' bolkar koi bhi voice note save kar sakte hain!",
                timestamp = System.currentTimeMillis(),
                isPinned = true
            )
        )
    }

    private fun saveNotes(list: List<VoiceNote>) {
        val jsonArray = JSONArray()
        for (note in list) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("timestamp", note.timestamp)
                put("isPinned", note.isPinned)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString("saved_notes", jsonArray.toString()).apply()
        _notes.value = list.sortedWith(compareByDescending<VoiceNote> { it.isPinned }.thenByDescending { it.timestamp })
    }

    fun addNote(title: String, content: String): VoiceNote {
        val note = VoiceNote(
            title = title.ifBlank { "Voice Note" },
            content = content
        )
        val current = _notes.value.toMutableList()
        current.add(0, note)
        saveNotes(current)
        return note
    }

    fun deleteNote(id: String) {
        val current = _notes.value.filter { it.id != id }
        saveNotes(current)
    }

    fun togglePin(id: String) {
        val current = _notes.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        saveNotes(current)
    }

    fun clearAll() {
        saveNotes(emptyList())
    }
}
