package com.example.data.model

enum class ActionType {
    NONE,
    OPEN_APP,
    SEARCH_WEB,
    LAUNCH_URL,
    SET_ALARM,
    CANCEL_ALARM,
    SET_TIMER,
    SET_REMINDER,
    TAKE_NOTE,
    SHOW_NOTES,
    OPEN_SETTINGS,
    DEVICE_INFO,
    BATTERY_INFO,
    DATE_TIME,
    SEND_MESSAGE,
    DIAL_PHONE,
    TOGGLE_FLASHLIGHT,
    SHARE_CONTENT,
    WHATSAPP_OPEN,
    WHATSAPP_MESSAGE,
    YOUTUBE_OPEN,
    YOUTUBE_SEARCH,
    CALL_PHONE
}

data class IntentCommand(
    val action: ActionType = ActionType.NONE,
    val target: String? = null,
    val rawQuery: String? = null,
    val parameters: Map<String, String> = emptyMap()
)
