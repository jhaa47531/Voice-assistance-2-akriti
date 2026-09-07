package com.example.domain.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.data.model.AppUsageInfo
import com.example.data.model.ScreenTimeSummary
import java.util.Calendar

class ScreenTimeManager(private val context: Context) {

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getScreenTimeSummary(): ScreenTimeSummary {
        if (!hasUsagePermission()) {
            return ScreenTimeSummary(hasPermission = false)
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeSummary(hasPermission = false)

        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis

        // Today: midnight to now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val midnightToday = calendar.timeInMillis

        // Yesterday: midnight yesterday to midnight today
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val midnightYesterday = calendar.timeInMillis

        // Query today's usage stats
        val todayStats = runCatching {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnightToday, now)
        }.getOrNull().orEmpty()

        // Query yesterday's usage stats
        val yesterdayStats = runCatching {
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, midnightYesterday, midnightToday)
        }.getOrNull().orEmpty()

        val pm = context.packageManager

        // Aggregate today's stats by package
        val todayAggregated = aggregateStats(todayStats)
        val todayTotalMillis = todayAggregated.values.sum()

        // Aggregate yesterday's stats
        val yesterdayAggregated = aggregateStats(yesterdayStats)
        val yesterdayTotalMillis = yesterdayAggregated.values.sum()

        // Top used apps (filter out minimal noise < 30 seconds)
        val topApps = todayAggregated.entries
            .filter { it.value >= 30_000L }
            .sortedByDescending { it.value }
            .take(6)
            .map { entry ->
                val appName = getAppLabel(pm, entry.key)
                AppUsageInfo(
                    packageName = entry.key,
                    appName = appName,
                    totalTimeMillis = entry.value,
                    formattedTime = formatDuration(entry.value)
                )
            }

        val comparisonText = when {
            yesterdayTotalMillis > 0L -> {
                val diff = todayTotalMillis - yesterdayTotalMillis
                val diffFormatted = formatDuration(kotlin.math.abs(diff))
                if (diff > 0) "+$diffFormatted vs yesterday" else "-$diffFormatted vs yesterday"
            }
            else -> "First day of tracked usage"
        }

        return ScreenTimeSummary(
            hasPermission = true,
            todayTotalMillis = todayTotalMillis,
            formattedToday = formatDuration(todayTotalMillis),
            yesterdayTotalMillis = yesterdayTotalMillis,
            formattedYesterday = formatDuration(yesterdayTotalMillis),
            topApps = topApps,
            dayComparisonText = comparisonText
        )
    }

    private fun aggregateStats(stats: List<UsageStats>): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        for (item in stats) {
            val pkg = item.packageName
            val time = item.totalTimeInForeground
            if (time > 0) {
                map[pkg] = (map[pkg] ?: 0L) + time
            }
        }
        return map
    }

    private fun getAppLabel(pm: PackageManager, packageName: String): String {
        val knownMap = mapOf(
            "com.google.android.youtube" to "YouTube",
            "com.whatsapp" to "WhatsApp",
            "com.android.chrome" to "Chrome",
            "com.instagram.android" to "Instagram",
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.maps" to "Google Maps",
            "com.google.android.gm" to "Gmail",
            "com.google.android.googlequicksearchbox" to "Google",
            "com.facebook.katana" to "Facebook",
            "com.twitter.android" to "X (Twitter)",
            "org.telegram.messenger" to "Telegram",
            "com.netflix.mediaclient" to "Netflix"
        )
        knownMap[packageName]?.let { return it }

        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        fun formatDuration(millis: Long): String {
            val totalMinutes = millis / (1000 * 60)
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return String.format(java.util.Locale.US, "%02dh %02dm", hours, minutes)
        }
    }
}
