package com.example.data.model

data class AkritiAlarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val label: String = "Akriti Alarm",
    val triggerTimeMillis: Long,
    val isActive: Boolean = true
)
