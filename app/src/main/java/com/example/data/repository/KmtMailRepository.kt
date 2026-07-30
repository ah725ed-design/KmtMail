package com.example.data.repository

import android.content.Context
import com.example.data.local.EmailHistoryEntity
import com.example.data.local.KmtMailDao
import com.example.data.local.KmtMailDatabase
import com.example.data.local.MessageEntity
import com.example.data.provider.ProviderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KmtMailRepository(
    private val dao: KmtMailDao,
    val providerManager: ProviderManager = ProviderManager()
) {
    init {
        // Purge any lingering mock/fake messages from previous app versions
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                dao.purgeNonApiMessages()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val currentEmailFlow: Flow<EmailHistoryEntity?> = dao.getCurrentEmailFlow()
    val historyFlow: Flow<List<EmailHistoryEntity>> = dao.getAllHistory()
    val activeProviderNameFlow: StateFlow<String> = providerManager.currentProviderName

    fun getMessagesForEmail(email: String): Flow<List<MessageEntity>> {
        return dao.getMessagesForEmail(email)
    }

    suspend fun generateNewEmail(preferredDomain: String = "kmtmail.com"): String = withContext(Dispatchers.IO) {
        val failoverResult = providerManager.generateAddressWithFailover(preferredDomain)
        val newAddress = failoverResult.getOrDefault(
            "kmt_${(1..6).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")}@$preferredDomain"
        )

        dao.resetCurrentFlags()
        val entity = EmailHistoryEntity(
            address = newAddress,
            createdAt = System.currentTimeMillis(),
            isCurrent = true,
            domain = preferredDomain
        )
        dao.insertEmailHistory(entity)

        // Immediately connect to active provider to fetch real inbox
        fetchAndSyncMessages(newAddress)

        return@withContext newAddress
    }

    suspend fun fetchAndSyncMessages(emailAddress: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Poll using active provider with failover support
            val providerResult = providerManager.fetchMessagesWithFailover(emailAddress)
            if (providerResult.isFailure) {
                return@withContext Result.failure(providerResult.exceptionOrNull() ?: Exception("Unknown error"))
            }

            val remoteMessages = providerResult.getOrDefault(emptyList())
            remoteMessages.forEach { msg ->
                dao.insertMessage(msg)
            }

            return@withContext Result.success(remoteMessages.size)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("لا توجد خدمة بريد متاحة حالياً، حاول مرة أخرى بعد قليل."))
        }
    }

    suspend fun getMessageById(id: String): MessageEntity? = withContext(Dispatchers.IO) {
        dao.getMessageById(id)
    }

    suspend fun markAsRead(id: String) = withContext(Dispatchers.IO) {
        dao.markAsRead(id)
    }

    suspend fun deleteMessage(id: String) = withContext(Dispatchers.IO) {
        dao.deleteMessage(id)
    }

    suspend fun clearCurrentInbox(emailAddress: String) = withContext(Dispatchers.IO) {
        dao.clearMessagesForEmail(emailAddress)
    }

    suspend fun switchEmail(address: String) = withContext(Dispatchers.IO) {
        dao.resetCurrentFlags()
        dao.setCurrentEmail(address)
    }

    suspend fun deleteEmailHistory(address: String) = withContext(Dispatchers.IO) {
        dao.deleteHistoryAddress(address)
        dao.clearMessagesForEmail(address)
    }

    companion object {
        @Volatile
        private var INSTANCE: KmtMailRepository? = null

        fun getInstance(context: Context): KmtMailRepository {
            return INSTANCE ?: synchronized(this) {
                val db = KmtMailDatabase.getDatabase(context)
                val repo = KmtMailRepository(db.kmtMailDao())
                INSTANCE = repo
                repo
            }
        }
    }
}
