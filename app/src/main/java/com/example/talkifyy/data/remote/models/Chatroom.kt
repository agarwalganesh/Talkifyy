package com.example.talkifyy.data.remote.models

data class Chatroom(
    val id: String = "",
    val userIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestampMs: Long = 0L
)

