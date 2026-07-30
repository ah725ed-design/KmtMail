package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val emailAddress: String,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val snippet: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isSaved: Boolean = false
)

@Entity(tableName = "email_history")
data class EmailHistoryEntity(
    @PrimaryKey val address: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isCurrent: Boolean = false,
    val domain: String = "kmtmail.com"
)
