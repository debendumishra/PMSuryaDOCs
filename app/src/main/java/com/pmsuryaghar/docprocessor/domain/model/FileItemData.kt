package com.pmsuryaghar.docprocessor.domain.model

import android.net.Uri

data class FileItemData(
    val name: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
