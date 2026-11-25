package com.screenx.ai.domain

import android.net.Uri
import java.io.File

enum class MediaKind { VIDEO, IMAGE }

/** One recording or screenshot in the library. [file] is set only on Android 8-9. */
data class MediaItem(
    val id: String,
    val uri: Uri,
    val fileName: String,
    val title: String,
    val sizeBytes: Long,
    val addedMs: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val file: File? = null
)

enum class SortOrder { NEWEST, OLDEST, NAME, SIZE, DURATION }

data class LibraryUiState(
    val visible: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val totalBytes: Long = 0L,
    val loading: Boolean = true,
    val query: String = "",
    val sort: SortOrder = SortOrder.NEWEST,
    val favoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet()
)
