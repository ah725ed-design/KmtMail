package com.example.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MessageEntity
import com.example.data.repository.KmtMailRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AppLanguage {
    ARABIC,
    ENGLISH
}

data class HomeUiState(
    val currentEmail: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val autoRefreshCountdown: Int = 10,
    val selectedDomain: String = "",
    val availableDomains: List<String> = emptyList(),
    val preferredProvider: String = "Auto",
    val language: AppLanguage = AppLanguage.ARABIC,
    val isDarkMode: Boolean = true,
    val activeProviderName: String = "Mail.tm",
    val errorMessage: String? = null
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KmtMailRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("kmtmail_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        val savedLangStr = prefs.getString("key_language", null)
        val savedLang = if (savedLangStr != null) {
            runCatching { AppLanguage.valueOf(savedLangStr) }.getOrDefault(AppLanguage.ARABIC)
        } else {
            val sysLang = java.util.Locale.getDefault().language
            if (sysLang.lowercase().startsWith("ar")) AppLanguage.ARABIC else AppLanguage.ENGLISH
        }

        val savedProvider = prefs.getString("key_preferred_provider", "Auto") ?: "Auto"
        val savedDarkMode = prefs.getBoolean("key_dark_mode", true)

        _uiState.value = _uiState.value.copy(
            language = savedLang,
            preferredProvider = savedProvider,
            isDarkMode = savedDarkMode
        )

        repository.setPreferredProvider(savedProvider)

        observeCurrentEmail()
        observeActiveProvider()
        observePreferredProvider()
        loadDynamicDomains()
    }

    private fun observeActiveProvider() {
        viewModelScope.launch {
            repository.activeProviderNameFlow.collectLatest { name ->
                _uiState.value = _uiState.value.copy(activeProviderName = name)
                loadDynamicDomains()
            }
        }
    }

    private fun observePreferredProvider() {
        viewModelScope.launch {
            repository.selectedProviderPreferenceFlow.collectLatest { pref ->
                _uiState.value = _uiState.value.copy(preferredProvider = pref)
            }
        }
    }

    fun loadDynamicDomains() {
        viewModelScope.launch {
            val domains = repository.getDynamicDomains()
            _uiState.value = _uiState.value.copy(
                availableDomains = domains,
                selectedDomain = if (domains.isNotEmpty()) domains.first() else _uiState.value.selectedDomain
            )
        }
    }

    fun setPreferredProvider(providerName: String) {
        prefs.edit().putString("key_preferred_provider", providerName).apply()
        repository.setPreferredProvider(providerName)
        _uiState.value = _uiState.value.copy(preferredProvider = providerName)
        loadDynamicDomains()
    }

    private fun observeCurrentEmail() {
        viewModelScope.launch {
            repository.currentEmailFlow.collectLatest { historyEntity ->
                if (historyEntity == null || historyEntity.address.startsWith("kmt_")) {
                    if (historyEntity?.address?.startsWith("kmt_") == true) {
                        repository.deleteEmailHistory(historyEntity.address)
                    }
                    if (_uiState.value.currentEmail.isBlank()) {
                        generateNewAddress()
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        currentEmail = historyEntity.address,
                        selectedDomain = historyEntity.domain
                    )
                    observeMessagesForEmail(historyEntity.address)
                    startAutoRefreshTimer()
                }
            }
        }
    }

    private fun observeMessagesForEmail(email: String) {
        viewModelScope.launch {
            repository.getMessagesForEmail(email).collectLatest { msgs ->
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("key_language", language.name).apply()
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("key_dark_mode", enabled).apply()
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun generateNewAddress(preferredDomain: String? = _uiState.value.selectedDomain.ifBlank { null }) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            val result = repository.generateNewEmail(preferredDomain)
            if (result.isSuccess) {
                val newAddress = result.getOrNull() ?: ""
                _uiState.value = _uiState.value.copy(
                    currentEmail = newAddress,
                    isRefreshing = false,
                    autoRefreshCountdown = 10,
                    errorMessage = null
                )
            } else {
                val err = result.exceptionOrNull()?.message ?: "جميع مزودي البريد غير متاحين حالياً"
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    errorMessage = err
                )
            }
            startAutoRefreshTimer()
        }
    }

    fun refreshInbox() {
        val email = _uiState.value.currentEmail
        if (email.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            val syncResult = repository.fetchAndSyncMessages(email)
            val err = if (syncResult.isFailure) syncResult.exceptionOrNull()?.message else null
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                autoRefreshCountdown = 10,
                errorMessage = err
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    fun clearInbox() {
        val email = _uiState.value.currentEmail
        if (email.isNotBlank()) {
            viewModelScope.launch {
                repository.clearCurrentInbox(email)
            }
        }
    }

    private fun startAutoRefreshTimer() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val current = _uiState.value.autoRefreshCountdown
                if (current <= 1) {
                    _uiState.value = _uiState.value.copy(autoRefreshCountdown = 10)
                    refreshInbox()
                } else {
                    _uiState.value = _uiState.value.copy(autoRefreshCountdown = current - 1)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
    }
}
