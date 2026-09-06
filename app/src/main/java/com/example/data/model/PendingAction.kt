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
}
