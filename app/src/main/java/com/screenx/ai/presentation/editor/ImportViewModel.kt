package com.screenx.ai.presentation.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screenx.ai.editor.ImportedMedia
import com.screenx.ai.editor.MediaImporter
import com.screenx.ai.editor.ProjectMediaPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val importing: Boolean = false,
    /** How many files of the last import could not be read. */
    val rejected: Int = 0
)

/** Reads the files picked by the user and puts them into the [ProjectMediaPool]. */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    val media: StateFlow<List<ImportedMedia>> = ProjectMediaPool.items

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(importing = true, rejected = 0) }
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val results = withContext(Dispatchers.IO) { uris.distinct().map { MediaImporter.probe(ctx, it) } }
            ProjectMediaPool.add(results.filterNotNull())
            _state.update { ImportUiState(importing = false, rejected = results.count { it == null }) }
        }
    }

    fun remove(id: Long) = ProjectMediaPool.remove(id)

    fun clear() = ProjectMediaPool.clear()

    fun dismissRejected() {
        _state.update { it.copy(rejected = 0) }
    }
}
