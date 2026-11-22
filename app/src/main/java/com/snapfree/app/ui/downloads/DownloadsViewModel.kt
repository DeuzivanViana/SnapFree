package com.snapfree.app.ui.downloads

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snapfree.app.AppContainer
import com.snapfree.app.data.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val active: List<DownloadEntity> = emptyList(),
    val history: List<DownloadEntity> = emptyList(),
    val loaded: Boolean = false,
)

class DownloadsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<DownloadsUiState> = container.downloads.downloads
        .map { list ->
            val (active, history) = list.partition { it.status.isActive }
            DownloadsUiState(active = active.sortedBy { it.createdAt }, history = history, loaded = true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    fun cancel(item: DownloadEntity) = container.downloads.cancel(item.id)

    fun retry(item: DownloadEntity) {
        viewModelScope.launch { container.downloads.retry(item) }
    }

    fun remove(item: DownloadEntity) {
        viewModelScope.launch { container.downloads.delete(item.id) }
    }

    /** Deletes the file from shared storage and removes the entry. */
    fun deleteFile(context: Context, item: DownloadEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            item.fileUri?.let { uri -> runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) } }
            container.downloads.delete(item.id)
        }
    }

    fun clearFinished() {
        viewModelScope.launch { container.downloads.clearFinished() }
    }
}
