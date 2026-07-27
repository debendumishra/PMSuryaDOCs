package com.pmsuryaghar.docprocessor.domain.model

import android.net.Uri

data class DocumentGroup(
    val documentType: DocumentType,
    val fileUris: List<Uri>
)
