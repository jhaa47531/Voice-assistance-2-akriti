package com.example.data.model

import java.util.UUID

data class CommandHistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val query: String,
    val actionType: ActionType = ActionType.NONE,
    val resultSummary: String,
    val isSuccess: Boolean = true
)
