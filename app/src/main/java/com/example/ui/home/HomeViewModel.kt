package com.example.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.EmailHistoryEntity
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
    val selectedDomain: String = "kmtmail.com",
    val language: AppLanguage = AppLanguage.ARABIC,
    val isDarkMode: Boolean = true,
    val activeProviderName: String = "Mail.tm API"
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KmtMailRepository.getInstance(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        observeCurrentEmail()
        observeActiveProvider()
    }

    private fun observeActiveProvider() {
        viewModelScope.launch {
            repository.activeProviderNameFlow.collectLatest { name ->
                _uiState.value = _uiState.value.copy(activeProviderName = name)
            }
        }
    }

    private fun observeCurrentEmail() {
        viewModelScope.launch {
            repository.currentEmailFlow.collectLatest { historyEntity ->
                if (historyEntity == null) {
                    generateNewAddress()
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
        _uiState.value = _uiState.value.copy(language = language)
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun generateNewAddress(preferredDomain: String = _uiState.value.selectedDomain) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val newAddress = repository.generateNewEmail(preferredDomain)
            _uiState.value = _uiState.value.copy(
                currentEmail = newAddress,
                isRefreshing = false,
                autoRefreshCountdown = 10
            )
            startAutoRefreshTimer()
        }
    }

    fun refreshInbox() {
        val email = _uiState.value.currentEmail
        if (email.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            repository.fetchAndSyncMessages(email)
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                autoRefreshCountdown = 10
            )
        }
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
