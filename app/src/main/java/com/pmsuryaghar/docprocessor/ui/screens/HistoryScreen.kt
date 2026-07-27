package com.pmsuryaghar.docprocessor.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pmsuryaghar.docprocessor.domain.model.ProcessingHistory
import com.pmsuryaghar.docprocessor.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val historyList by viewModel.historyList.collectAsState()
    var selectedItem by remember { mutableStateOf<ProcessingHistory?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Processing History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearHistory() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No history found. Start processing documents first!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyList) { item ->
                    HistoryItemCard(item = item, onClick = { selectedItem = item })
                }
            }
        }

        // Details dialog
        selectedItem?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedItem = null },
                title = { Text(item.customerName, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Mobile: ${item.mobileNumber}", fontWeight = FontWeight.Medium)
                        
                        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(item.processingDate))
                        Text("Processed on: $dateStr")
                        
                        Text("Status: ${item.status}", color = if (item.status == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Output folder Uri:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(item.folderPath, style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ZIP Uri:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Text(item.zipPath, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    Button(onClick = { selectedItem = null }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    Row {
                        IconButton(onClick = { openFolder(context, item.folderPath) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open Folder")
                        }
                        IconButton(onClick = { shareZip(context, item.zipPath) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share ZIP")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun HistoryItemCard(
    item: ProcessingHistory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.customerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    item.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.status == "COMPLETED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mobile: ${item.mobileNumber}", style = MaterialTheme.typography.bodyMedium)
                val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date(item.processingDate))
                Text(dateStr, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun openFolder(context: Context, folderUriString: String) {
    try {
        val uri = Uri.parse(folderUriString)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "resource/folder")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback open document tree launcher
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}

private fun shareZip(context: Context, zipUriString: String) {
    try {
        val uri = Uri.parse(zipUriString)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share ZIP File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
