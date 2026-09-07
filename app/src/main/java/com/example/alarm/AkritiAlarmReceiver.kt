package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.repository.AkritiAlarmStore

class AkritiAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE_ALARM = "com.example.alarm.ACTION_FIRE_ALARM"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_HOUR = "extra_alarm_hour"
        const val EXTRA_ALARM_MINUTE = "extra_alarm_minute"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"

        private const val CHANNEL_ID = "akriti_alarm_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val hour = intent.getIntExtra(EXTRA_ALARM_HOUR, 7)
        val minute = intent.getIntExtra(EXTRA_ALARM_MINUTE, 0)
        val label = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: "Akriti Alarm"

        // Mark alarm as triggered in store
        if (alarmId != -1) {
            val store = AkritiAlarmStore(context)
            store.cancelAlarm(alarmId)
        }

        // Show Alarm Notification
        showAlarmNotification(context, alarmId, hour, minute, label)
    }

    private fun showAlarmNotification(
        context: Context,
        alarmId: Int,
        hour: Int,
        minute: Int,
        label: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Akriti Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications set by Akriti Voice Assistant"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (alarmId != -1) alarmId else 100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeString = String.format("%02d:%02d", hour, minute)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $label")
            .setContentText("Alarm for $timeString")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(if (alarmId != -1) alarmId else 1001, notification)
    }
}
