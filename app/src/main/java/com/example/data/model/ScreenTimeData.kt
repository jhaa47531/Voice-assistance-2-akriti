package com.example.data.model

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val totalTimeMillis: Long,
    val formattedTime: String
)

data class ScreenTimeSummary(
    val hasPermission: Boolean,
    val todayTotalMillis: Long = 0L,
    val formattedToday: String = "--h --m",
    val yesterdayTotalMillis: Long = 0L,
    val formattedYesterday: String = "--h --m",
    val topApps: List<AppUsageInfo> = emptyList(),
    val dayComparisonText: String = ""
)
