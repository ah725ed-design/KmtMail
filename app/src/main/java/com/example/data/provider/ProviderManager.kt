package com.example.data.provider

import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class ProviderManager(
    private val providers: List<TempMailProvider> = listOf(
        SecMailProvider(),
        MailTmProvider()
    )
) {
    private var activeIndex = 0

    private val _currentProviderName = MutableStateFlow(providers[0].providerName)
    val currentProviderName: StateFlow<String> = _currentProviderName.asStateFlow()

    suspend fun generateAddressWithFailover(preferredDomain: String? = null): Result<String> {
        val startIndex = activeIndex
        for (i in providers.indices) {
            val providerIndex = (startIndex + i) % providers.size
            val provider = providers[providerIndex]

            val address = withTimeoutOrNull(8000L) {
                runCatching { provider.generateAddress(preferredDomain) }.getOrNull()
            }

            if (!address.isNullOrBlank()) {
                activeIndex = providerIndex
                _currentProviderName.value = provider.providerName
                return Result.success(address)
            }
        }

        return Result.failure(
            Exception("لا توجد خدمة بريد متاحة حاليًا، حاول مرة أخرى بعد قليل.")
        )
    }

    suspend fun fetchMessagesWithFailover(emailAddress: String): Result<List<MessageEntity>> {
        val startIndex = activeIndex
        for (i in providers.indices) {
            val providerIndex = (startIndex + i) % providers.size
            val provider = providers[providerIndex]

            val messages = withTimeoutOrNull(8000L) {
                runCatching { provider.fetchMessages(emailAddress) }.getOrNull()
            }

            if (messages != null) {
                activeIndex = providerIndex
                _currentProviderName.value = provider.providerName
                return Result.success(messages)
            }
        }

        return Result.failure(
            Exception("لا توجد خدمة بريد متاحة حاليًا، حاول مرة أخرى بعد قليل.")
        )
    }
}
