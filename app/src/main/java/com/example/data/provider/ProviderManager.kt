package com.example.data.provider

import android.content.Context
import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

class ProviderManager(context: Context? = null) {

    private val prefs = context?.getSharedPreferences("kmtmail_prefs", Context.MODE_PRIVATE)

    private val mailTm = MailTmProvider()
    private val mailGw = MailGwProvider()
    private val secMail = SecMailProvider()
    private val tempMail = TempMailLolProvider()

    private val allProviders: List<TempMailProvider> = listOf(
        mailTm,
        mailGw,
        secMail,
        tempMail
    )

    private val initialPref = prefs?.getString("key_preferred_provider", "Auto") ?: "Auto"

    private val _selectedProviderPreference = MutableStateFlow(initialPref)
    val selectedProviderPreference: StateFlow<String> = _selectedProviderPreference.asStateFlow()

    private val initialProviderName = if (initialPref != "Auto") {
        allProviders.find { it.providerName.equals(initialPref, ignoreCase = true) }?.providerName ?: mailTm.providerName
    } else {
        mailTm.providerName
    }

    private val _currentProviderName = MutableStateFlow(initialProviderName)
    val currentProviderName: StateFlow<String> = _currentProviderName.asStateFlow()

    fun setPreferredProvider(providerName: String) {
        prefs?.edit()?.putString("key_preferred_provider", providerName)?.apply()
        _selectedProviderPreference.value = providerName
        if (providerName != "Auto") {
            val matched = allProviders.find { it.providerName.equals(providerName, ignoreCase = true) }
            if (matched != null) {
                _currentProviderName.value = matched.providerName
            }
        }
    }

    private fun getOrderedProviders(): List<TempMailProvider> {
        val pref = _selectedProviderPreference.value
        if (pref != "Auto") {
            val preferred = allProviders.find { it.providerName.equals(pref, ignoreCase = true) }
            if (preferred != null) {
                // Preferred first, then others as failover
                return listOf(preferred) + allProviders.filter { it != preferred }
            }
        }
        return allProviders
    }

    suspend fun getActiveDomains(): List<String> {
        val activeProvider = allProviders.find { it.providerName == _currentProviderName.value } ?: mailTm
        val domains = runCatching { activeProvider.getAvailableDomains() }.getOrDefault(emptyList())
        return domains.ifEmpty {
            // Fallback try all providers for dynamic domains
            for (provider in allProviders) {
                val doms = runCatching { provider.getAvailableDomains() }.getOrDefault(emptyList())
                if (doms.isNotEmpty()) return doms
            }
            emptyList()
        }
    }

    suspend fun generateAddressWithFailover(preferredDomain: String? = null): Result<String> {
        val providersToTry = getOrderedProviders()

        for (provider in providersToTry) {
            val address = withTimeoutOrNull(8000L) {
                runCatching { provider.generateAddress(preferredDomain) }.getOrNull()
            }

            if (!address.isNullOrBlank()) {
                if (_selectedProviderPreference.value == "Auto") {
                    _currentProviderName.value = provider.providerName
                } else {
                    val matched = allProviders.find { it.providerName.equals(_selectedProviderPreference.value, ignoreCase = true) }
                    _currentProviderName.value = matched?.providerName ?: provider.providerName
                }
                return Result.success(address)
            }
        }

        return Result.failure(
            Exception("جميع مزودي البريد غير متاحين حالياً")
        )
    }

    suspend fun fetchMessagesWithFailover(emailAddress: String): Result<List<MessageEntity>> {
        val providersToTry = getOrderedProviders()

        for (provider in providersToTry) {
            val messages = withTimeoutOrNull(8000L) {
                runCatching { provider.fetchMessages(emailAddress) }.getOrNull()
            }

            if (messages != null) {
                if (_selectedProviderPreference.value == "Auto") {
                    _currentProviderName.value = provider.providerName
                }
                return Result.success(messages)
            }
        }

        return Result.failure(
            Exception("جميع مزودي البريد غير متاحين حالياً")
        )
    }
}
