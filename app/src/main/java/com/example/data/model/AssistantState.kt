package com.example.data.model

enum class AssistantState {
    IDLE,
    LISTENING_FOR_WAKE_WORD,
    WAKE_DETECTED,
    LISTENING,
    LISTENING_FOR_COMMAND,
    PROCESSING,
    EXECUTING,
    SPEAKING,
    ERROR;

    val isListening: Boolean
        get() = this == LISTENING || this == LISTENING_FOR_COMMAND || this == LISTENING_FOR_WAKE_WORD
}
