package com.example.data.model

sealed class PendingAction {
    data class SendWhatsAppMessage(
        val contactName: String,
        val phoneNumber: String,
        val messageText: String
    ) : PendingAction()

    data class MakePhoneCall(
        val contactName: String,
        val phoneNumber: String
    ) : PendingAction()

    data class DisambiguateContact(
        val contacts: List<ContactMatch>,
        val targetAction: ActionType,
        val pendingMessage: String? = null
    ) : PendingAction()
}
