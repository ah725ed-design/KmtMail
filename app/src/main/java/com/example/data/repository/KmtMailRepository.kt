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
        // Purge any lingering mock/fake messages and legacy kmt_ addresses from previous app versions
        GlobalScope.launch(Dispatchers.IO) {
            try {
                dao.purgeLegacyKmtAddresses()
                dao.purgeLegacyKmtMessages()
                dao.purgeNonApiMessages()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val currentEmailFlow: Flow<EmailHistoryEntity?> = dao.getCurrentEmailFlow()
    val historyFlow: Flow<List<EmailHistoryEntity>> = dao.getAllHistory()
    val activeProviderNameFlow: StateFlow<String> = providerManager.currentProviderName
    val selectedProviderPreferenceFlow: StateFlow<String> = providerManager.selectedProviderPreference

    fun setPreferredProvider(providerName: String) {
        providerManager.setPreferredProvider(providerName)
    }

    suspend fun getDynamicDomains(): List<String> = withContext(Dispatchers.IO) {
        providerManager.getActiveDomains()
    }

    fun getMessagesForEmail(email: String): Flow<List<MessageEntity>> {
        return dao.getMessagesForEmail(email)
    }

    suspend fun generateNewEmail(preferredDomain: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val failoverResult = providerManager.generateAddressWithFailover(preferredDomain)
        if (failoverResult.isFailure) {
            return@withContext Result.failure(
                failoverResult.exceptionOrNull() ?: Exception("جميع مزودي البريد غير متاحين حالياً")
            )
        }

        val newAddress = failoverResult.getOrNull()
            ?: return@withContext Result.failure(Exception("جميع مزودي البريد غير متاحين حالياً"))

        val domain = newAddress.substringAfter("@", preferredDomain ?: "mailtm.com")

        dao.resetCurrentFlags()
        val entity = EmailHistoryEntity(
            address = newAddress,
            createdAt = System.currentTimeMillis(),
            isCurrent = true,
            domain = domain
        )
        dao.insertEmailHistory(entity)

        // Fetch inbox from real API
        fetchAndSyncMessages(newAddress)

        return@withContext Result.success(newAddress)
    }

    suspend fun fetchAndSyncMessages(emailAddress: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
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
            return@withContext Result.failure(Exception("جميع مزودي البريد غير متاحين حالياً"))
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
