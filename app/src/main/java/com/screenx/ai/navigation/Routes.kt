package com.screenx.ai.navigation

import android.net.Uri

/** Routes that are not in the bottom bar. */
object Routes {
    const val EDIT_PATTERN = "edit/{uri}"

    fun edit(uri: Uri): String = "edit/${Uri.encode(uri.toString())}"
}
