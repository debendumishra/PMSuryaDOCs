package com.pmsuryaghar.docprocessor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFormDialog(
    documentName: String,
    fields: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit
) {
    // State to hold the values entered by the user
    val formState = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Generate $documentName")
        },
        text = {
            if (fields.isEmpty()) {
                Text("No placeholders found in this document.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(fields) { field ->
                        OutlinedTextField(
                            value = formState[field] ?: "",
                            onValueChange = { formState[field] = it },
                            label = { Text(field) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(formState.toMap()) },
                enabled = fields.isEmpty() || formState.size == fields.size && formState.values.all { it.isNotBlank() }
            ) {
                Text("Generate PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
