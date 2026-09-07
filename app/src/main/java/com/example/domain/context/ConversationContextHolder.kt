package com.example.domain.context

data class ConversationContext(
    val lastContactName: String? = null,
    val lastContactNumber: String? = null,
    val lastAlarmHour: Int? = null,
    val lastAlarmMinute: Int? = null,
    val lastAppOpened: String? = null,
    val lastSearchQuery: String? = null,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    val isFresh: Boolean
        get() = (System.currentTimeMillis() - lastUpdatedMillis) < 10 * 60 * 1000L
}

class ConversationContextHolder {
    @Volatile
    private var currentContext = ConversationContext()

    fun updateContact(name: String?, number: String? = null) {
        if (!name.isNullOrBlank()) {
            currentContext = currentContext.copy(
                lastContactName = name.trim(),
                lastContactNumber = number?.trim(),
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }
    }

    fun updateAlarm(hour: Int?, minute: Int?) {
        if (hour != null) {
            currentContext = currentContext.copy(
                lastAlarmHour = hour,
                lastAlarmMinute = minute ?: 0,
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }
    }

    fun updateApp(appName: String?) {
        if (!appName.isNullOrBlank()) {
            currentContext = currentContext.copy(
                lastAppOpened = appName.trim(),
                lastUpdatedMillis = System.currentTimeMillis()
            )
        }
    }

    fun getContext(): ConversationContext? {
        return if (currentContext.isFresh) currentContext else null
    }

    fun clear() {
        currentContext = ConversationContext()
    }
}
