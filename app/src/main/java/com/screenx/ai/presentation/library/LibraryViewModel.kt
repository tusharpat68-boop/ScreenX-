package com.screenx.ai.presentation.library

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenx.ai.R
import com.screenx.ai.ScreenXApp
import com.screenx.ai.domain.LibraryUiState
import com.screenx.ai.domain.MediaItem
import com.screenx.ai.domain.MediaKind
import com.screenx.ai.domain.SortOrder
import com.screenx.ai.screenshot.ScreenshotEvents
import com.screenx.ai.storage.FailReason
import com.screenx.ai.storage.LibraryResult
import com.screenx.ai.storage.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PendingApproval(val sender: IntentSender, val retry: (() -> Unit)?)

private data class Loaded(val items: List<MediaItem> = emptyList(), val loading: Boolean = true)

/** Shared logic for the recordings and the screenshots screen. */
open class LibraryViewModel(application: Application, val kind: MediaKind) : AndroidViewModel(application) {

    private val library = MediaLibrary(application, kind)
    protected val settings = (application as ScreenXApp).settingsRepository

    private val loaded = MutableStateFlow(Loaded())
    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(SortOrder.NEWEST)
    private val favoritesOnly = MutableStateFlow(false)
    private val favorites = settings.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val ui: StateFlow<LibraryUiState> = combine(loaded, query, sort, favoritesOnly, favorites) { l, q, s, fo, fav ->
        val filtered = l.items.filter { item ->
            (q.isBlank() || item.title.contains(q.trim(), ignoreCase = true)) &&
                (!fo || item.fileName in fav)
        }
        val sorted = when (s) {
            SortOrder.NEWEST -> filtered.sortedByDescending { it.addedMs }
            SortOrder.OLDEST -> filtered.sortedBy { it.addedMs }
            SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
        LibraryUiState(
            visible = sorted,
            totalCount = l.items.size,
            totalBytes = l.items.sumOf { it.sizeBytes },
            loading = l.loading,
            query = q,
            sort = s,
            favoritesOnly = fo,
            favorites = fav
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    /** A change Android wants the user to approve (see [LibraryResult.NeedsApproval]). */
    val approval = MutableStateFlow<PendingApproval?>(null)

    /** A short message (string resource) for the screen to show once. */
    val message = MutableStateFlow<Int?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = library.list()
            loaded.value = Loaded(list, loading = false)
        }
    }

    fun setQuery(q: String) { query.value = q }
    fun setSort(s: SortOrder) { sort.value = s }
    fun toggleFavoritesOnly() { favoritesOnly.value = !favoritesOnly.value }
    fun clearMessage() { message.value = null }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            settings.setFavorite(item.fileName, item.fileName !in favorites.value)
        }
    }

    fun viewIntent(item: MediaItem): Intent = library.viewIntent(item)
    fun shareIntent(item: MediaItem): Intent = library.shareIntent(item)
    fun locationOf(item: MediaItem): String = library.locationLabel(item)
    suspend fun estimateFps(item: MediaItem): Int? = library.estimateFps(item)

    fun delete(item: MediaItem) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { library.delete(item) }
            handle(result, retry = { delete(item) }) { message.value = R.string.msg_deleted }
        }
    }

    fun rename(item: MediaItem, newTitle: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { library.rename(item, newTitle) }
            handle(result, retry = { rename(item, newTitle) }) { done ->
                val newName = done.newFileName
                if (newName != null && newName != item.fileName) {
                    viewModelScope.launch { settings.renameFavorite(item.fileName, newName) }
                }
                message.value = R.string.msg_renamed
            }
        }
    }

    fun onApprovalResult(granted: Boolean) {
        val pending = approval.value
        approval.value = null
        if (granted) {
            val retry = pending?.retry
            if (retry != null) retry() else refresh()
        }
    }

    private fun handle(result: LibraryResult, retry: () -> Unit, onDone: (LibraryResult.Done) -> Unit) {
        when (result) {
            is LibraryResult.Done -> {
                onDone(result)
                refresh()
            }
            is LibraryResult.NeedsApproval ->
                approval.value = PendingApproval(result.sender, if (result.retry) retry else null)
            is LibraryResult.Failed -> message.value = when (result.reason) {
                FailReason.INVALID_NAME -> R.string.err_invalid_name
                FailReason.NAME_EXISTS -> R.string.err_name_exists
                FailReason.OPERATION_FAILED -> R.string.err_operation_failed
            }
        }
    }
}

class RecordingsViewModel(application: Application) : LibraryViewModel(application, MediaKind.VIDEO)

class ScreenshotsViewModel(application: Application) : LibraryViewModel(application, MediaKind.IMAGE) {

    val delaySec: StateFlow<Int> = settings.screenshotDelaySec
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)

    init {
        viewModelScope.launch {
            ScreenshotEvents.saved.collect { refresh() }
        }
    }

    fun setDelay(seconds: Int) {
        viewModelScope.launch { settings.setScreenshotDelay(seconds) }
    }
}
