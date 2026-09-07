package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.example.data.model.AkritiAlarm
import com.example.data.repository.AkritiAlarmStore
import java.util.Calendar

class AkritiAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val alarmStore = AkritiAlarmStore(context)

    companion object {
        private const val TAG = "AkritiAlarmScheduler"
    }

    /**
     * Schedules an alarm with AlarmManager using a deterministic, reconstructible PendingIntent identity.
     */
    fun scheduleAlarm(hour: Int, minute: Int, label: String = "Akriti Alarm"): AkritiAlarm {
        val finalHour = hour.coerceIn(0, 23)
        val finalMinute = minute.coerceIn(0, 59)

        // Calculate trigger epoch time
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, finalHour)
            set(Calendar.MINUTE, finalMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Unique deterministic ID for this time slot (e.g. 7:00 -> 700, 19:30 -> 1930)
        val alarmId = finalHour * 100 + finalMinute

        val alarm = AkritiAlarm(
            id = alarmId,
            hour = finalHour,
            minute = finalMinute,
            label = label,
            triggerTimeMillis = calendar.timeInMillis,
            isActive = true
        )

        // Save to persistent metadata store
        alarmStore.saveAlarm(alarm)

        // Construct PendingIntent
        val pendingIntent = createAlarmPendingIntent(alarm)

        try {
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scheduling AlarmManager exact alarm, falling back to set()", e)
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        // Also notify system Clock app if available (non-blocking)
        try {
            val clockIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_HOUR, finalHour)
                putExtra(AlarmClock.EXTRA_MINUTES, finalMinute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (clockIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(clockIntent)
            }
        } catch (_: Exception) {}

        return alarm
    }

    /**
     * Cancels the scheduled alarm by reconstructing the EXACT SAME PendingIntent identity.
     */
    fun cancelAlarm(alarm: AkritiAlarm): Boolean {
        return try {
            val pendingIntent = createAlarmPendingIntent(alarm)
            alarmManager?.cancel(pendingIntent)
            pendingIntent.cancel()

            // Update store
            alarmStore.cancelAlarm(alarm.id)
            alarmStore.removeAlarm(alarm.id)

            // Try to dismiss in system Clock app if supported
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_TIME)
                        putExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (dismissIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(dismissIntent)
                    }
                }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel alarm with id ${alarm.id}", e)
            false
        }
    }

    /**
     * Reconstructs the exact PendingIntent identity for scheduling or cancelling.
     */
    fun createAlarmPendingIntent(alarm: AkritiAlarm): PendingIntent {
        val intent = Intent(context, AkritiAlarmReceiver::class.java).apply {
            action = AkritiAlarmReceiver.ACTION_FIRE_ALARM
            putExtra(AkritiAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AkritiAlarmReceiver.EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(AkritiAlarmReceiver.EXTRA_ALARM_MINUTE, alarm.minute)
            putExtra(AkritiAlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun findMatchingAlarms(hour: Int, minute: Int?): List<AkritiAlarm> {
        return alarmStore.findAlarms(hour, minute)
    }

    fun getActiveAlarms(): List<AkritiAlarm> {
        return alarmStore.getActiveAlarms()
    }
}
