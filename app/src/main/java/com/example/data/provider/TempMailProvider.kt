package com.example.data.provider

import com.example.data.local.MessageEntity

interface TempMailProvider {
    val providerName: String
    suspend fun getAvailableDomains(): List<String>
    suspend fun generateAddress(preferredDomain: String? = null): String
    suspend fun fetchMessages(emailAddress: String): List<MessageEntity>
}
