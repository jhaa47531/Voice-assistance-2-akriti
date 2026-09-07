package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.AkritiAlarm
import org.json.JSONArray
import org.json.JSONObject

class AkritiAlarmStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("akriti_alarms_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALARMS = "stored_alarms"
    }

    @Synchronized
    fun getAllAlarms(): List<AkritiAlarm> {
        val rawJson = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        val list = mutableListOf<AkritiAlarm>()
        try {
            val jsonArray = JSONArray(rawJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    AkritiAlarm(
                        id = obj.getInt("id"),
                        hour = obj.getInt("hour"),
                        minute = obj.getInt("minute"),
                        label = obj.optString("label", "Akriti Alarm"),
                        triggerTimeMillis = obj.getLong("triggerTimeMillis"),
                        isActive = obj.optBoolean("isActive", true)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    fun getActiveAlarms(): List<AkritiAlarm> {
        return getAllAlarms().filter { it.isActive }
    }

    @Synchronized
    fun saveAlarm(alarm: AkritiAlarm) {
        val current = getAllAlarms().toMutableList()
        current.removeAll { it.id == alarm.id }
        current.add(alarm)
        persistAlarms(current)
    }

    @Synchronized
    fun findAlarms(hour: Int, minute: Int?): List<AkritiAlarm> {
        val active = getActiveAlarms()
        return if (minute != null) {
            active.filter { it.hour == hour && it.minute == minute }
        } else {
            active.filter { it.hour == hour }
        }
    }

    @Synchronized
    fun cancelAlarm(id: Int): Boolean {
        val current = getAllAlarms().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = current[index].copy(isActive = false)
            current[index] = updated
            persistAlarms(current)
            return true
        }
        return false
    }

    @Synchronized
    fun removeAlarm(id: Int): Boolean {
        val current = getAllAlarms().toMutableList()
        val removed = current.removeAll { it.id == id }
        if (removed) {
            persistAlarms(current)
        }
        return removed
    }

    @Synchronized
    fun clearAll() {
        prefs.edit().remove(KEY_ALARMS).apply()
    }

    private fun persistAlarms(alarms: List<AkritiAlarm>) {
        val array = JSONArray()
        for (alarm in alarms) {
            val obj = JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("label", alarm.label)
                put("triggerTimeMillis", alarm.triggerTimeMillis)
                put("isActive", alarm.isActive)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }
}
