package com.snapfree.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfree.app.AppContainer
import com.snapfree.app.data.AppSettings
import com.snapfree.app.data.LoginSite
import com.snapfree.app.data.SettingsRepository
import com.snapfree.app.engine.EngineState
import com.snapfree.app.engine.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val engineState: StateFlow<EngineState> = container.engine.state
    val updating: StateFlow<Boolean> = container.engine.updating

    val loggedIn: StateFlow<Set<LoginSite>> = container.cookies.loggedIn

    private val _hasCookies = MutableStateFlow(container.cookies.hasManual())
    val hasCookies: StateFlow<Boolean> = _hasCookies.asStateFlow()

    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()

    /** Runs a settings write in the view model scope. */
    fun edit(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settings.block() }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _updateResult.value = container.engine.update(settings.value.updateChannel)
        }
    }

    fun consumeUpdateResult() {
        _updateResult.value = null
    }

    fun saveCookies(text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.cookies.saveManual(text)
            _hasCookies.value = container.cookies.hasManual()
            if (_hasCookies.value) container.settings.setUseCookies(true)
        }
    }

    fun clearCookies() {
        viewModelScope.launch(Dispatchers.IO) {
            container.cookies.clearManual()
            container.settings.setUseCookies(false)
            _hasCookies.value = false
        }
    }

    suspend fun readCookies(): String = withContext(Dispatchers.IO) {
        container.cookies.manualFile.takeIf { it.exists() }?.readText().orEmpty()
    }

    fun logout(site: LoginSite) {
        container.cookies.logout(site)
    }
}
