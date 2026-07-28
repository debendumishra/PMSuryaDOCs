package com.pmsuryaghar.docprocessor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pmsuryaghar.docprocessor.data.util.FileUtils
import com.pmsuryaghar.docprocessor.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCleanupScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val sourceFiles by viewModel.cleanupSourceFiles.collectAsState()
    val destFiles by viewModel.cleanupDestFiles.collectAsState()
    val whatsappDocsFiles by viewModel.cleanupWhatsappDocsFiles.collectAsState()
    val whatsappImagesFiles by viewModel.cleanupWhatsappImagesFiles.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    
    // Active navigation stack for subfolders: Pair(Name, Uri)
    var currentSubfolder by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var activeSubfolderFiles by remember { mutableStateOf<List<Triple<String, Uri, Boolean>>>(emptyList()) }
    var isLoadingSubfolder by remember { mutableStateOf(false) }
    
    val selectedUris = remember { mutableStateMapOf<Uri, Boolean>() }
    
    var renameTarget by remember { mutableStateOf<Pair<String, Uri>?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }

    // Helper to refresh files
    fun refreshCurrentView() {
        viewModel.loadCleanupFiles(context)
        val activeFolder = currentSubfolder
        if (activeFolder != null) {
            coroutineScope.launch {
                isLoadingSubfolder = true
                activeSubfolderFiles = viewModel.loadFolderContents(context, activeFolder.second)
                isLoadingSubfolder = false
            }
        }
    }
    
    // Refresh files when screen loads
    LaunchedEffect(Unit) {
        viewModel.loadCleanupFiles(context)
    }
    
    // Async subfolder loading when currentSubfolder changes
    LaunchedEffect(currentSubfolder) {
        val folder = currentSubfolder
        if (folder != null) {
            isLoadingSubfolder = true
            activeSubfolderFiles = viewModel.loadFolderContents(context, folder.second)
            isLoadingSubfolder = false
        } else {
            activeSubfolderFiles = emptyList()
        }
    }
    
    // Determine current files list based on selected tab and subfolder state
    // Cap at 100 items for performance — WA folders can have thousands of files
    val MAX_DISPLAY = 100
    val rawCurrentFiles = when {
        currentSubfolder != null -> activeSubfolderFiles.take(MAX_DISPLAY)
        selectedTab == 0 -> sourceFiles
        selectedTab == 1 -> destFiles
        selectedTab == 2 -> whatsappDocsFiles.take(MAX_DISPLAY)
        else -> whatsappImagesFiles.take(MAX_DISPLAY)
    }
    val totalCurrentCount = when {
        currentSubfolder != null -> activeSubfolderFiles.size
        selectedTab == 0 -> sourceFiles.size
        selectedTab == 1 -> destFiles.size
        selectedTab == 2 -> whatsappDocsFiles.size
        else -> whatsappImagesFiles.size
    }
    
    LaunchedEffect(rawCurrentFiles, selectedTab, currentSubfolder) {
        selectedUris.clear()
        rawCurrentFiles.forEach { selectedUris[it.second] = false }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Storage & File Manager", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (currentSubfolder != null) "Subfolder: ${currentSubfolder?.first}"
                                   else if (selectedTab == 0) "Source Folder"
                                   else if (selectedTab == 1) "Destination Folder"
                                   else if (selectedTab == 2) "WhatsApp Documents"
                                   else "WhatsApp Images",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSubfolder != null) {
                            currentSubfolder = null
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 4 Folders Tab Header Row (Source, Destination, WhatsApp Documents, WhatsApp Images)
            if (currentSubfolder == null) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 12.dp
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            currentSubfolder = null
                        },
                        text = { Text("Source (${sourceFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            currentSubfolder = null
                        },
                        text = { Text("Destination (${destFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            currentSubfolder = null
                        },
                        text = { Text("WA Docs (${whatsappDocsFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = {
                            selectedTab = 3
                            currentSubfolder = null
                        },
                        text = { Text("WA Images (${whatsappImagesFiles.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }
            } else {
                // Breadcrumb navigation header inside subfolder
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Root > ${currentSubfolder?.first}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { currentSubfolder = null },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Up Directory", fontSize = 12.sp)
                        }
                    }
                }
            }
            
            if (rawCurrentFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No files found in this directory.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (currentSubfolder != null) "Subfolder is empty."
                            else if (selectedTab == 0) "Source folder is empty or not configured."
                            else if (selectedTab == 1) "No output files or customer directories created yet."
                            else if (selectedTab == 2) "No WhatsApp document files received in the last 2 days.\nConfigure WA Documents folder in Settings."
                            else "No WhatsApp image files received in the last 2 days.\nConfigure WA Images folder in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    // Select All Row
                    val allSelected = rawCurrentFiles.isNotEmpty() && rawCurrentFiles.all { selectedUris[it.second] == true }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Select All Items (${rawCurrentFiles.size})",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { isChecked ->
                                rawCurrentFiles.forEach { selectedUris[it.second] = isChecked }
                            }
                        )
                    }
                    
                    // Subfolder loading indicator
                    if (isLoadingSubfolder) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Sync button for WA tabs (tab 2 and 3)
                    if (currentSubfolder == null && (selectedTab == 2 || selectedTab == 3)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.syncWhatsappFiles(context, selectedTab == 2) },
                                modifier = Modifier.wrapContentWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF16A34A)
                                )
                            ) {
                                Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync from WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (syncStatus.isNotEmpty()) {
                                Text(
                                    text = syncStatus,
                                    fontSize = 11.sp,
                                    color = if (syncStatus.contains("fail", ignoreCase = true))
                                        Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    
                    // File count badge
                    if (totalCurrentCount > MAX_DISPLAY) {
                        Text(
                            "Showing $MAX_DISPLAY of $totalCurrentCount items (most recent first)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    // Lazy List of Files/Folders
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rawCurrentFiles, key = { it.second.toString() }) { (name, uri, isDirectory) ->
                            var showItemMenu by remember { mutableStateOf(false) }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDirectory) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f) 
                                                      else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isDirectory) {
                                            // Open subfolder — LaunchedEffect(currentSubfolder) will load contents async
                                            currentSubfolder = Pair(name, uri)
                                        } else {
                                            // Open file via view intent
                                            try {
                                                val type = context.contentResolver.getType(uri) ?: "*/*"
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, type)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {}
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedUris[uri] == true,
                                        onCheckedChange = { isChecked ->
                                            selectedUris[uri] = isChecked
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isDirectory) Icons.Default.Folder 
                                                       else if (name.endsWith(".pdf", true)) Icons.Default.PictureAsPdf
                                                       else if (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true)) Icons.Default.Image
                                                       else Icons.Default.Description,
                                        contentDescription = if (isDirectory) "Folder" else "File",
                                        tint = if (isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isDirectory) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isDirectory) "Folder • Tap to open" else "File • Tap to view",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Item Overflow Menu (File operations)
                                    Box {
                                        IconButton(onClick = { showItemMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = showItemMenu,
                                            onDismissRequest = { showItemMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Open / View") },
                                                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                                                onClick = {
                                                    showItemMenu = false
                                                    if (isDirectory) {
                                                        currentSubfolder = Pair(name, uri)
                                                        activeSubfolderFiles = viewModel.loadFolderContents(context, uri)
                                                    } else {
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {}
                                                    }
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Send to Source Folder") },
                                                leadingIcon = { Icon(Icons.Default.Input, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showItemMenu = false
                                                    viewModel.sendFilesToSourceFolder(context, listOf(uri))
                                                    refreshCurrentView()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Share") },
                                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                onClick = {
                                                    showItemMenu = false
                                                    FileUtils.shareFiles(context, listOf(uri))
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = {
                                                    showItemMenu = false
                                                    renameTarget = Pair(name, uri)
                                                    renameText = name
                                                    showRenameDialog = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    showItemMenu = false
                                                    viewModel.deleteCleanupFiles(context, listOf(uri), selectedTab != 1)
                                                    refreshCurrentView()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Selected Items File Operations Toolbar (Structured Responsive Layout - Requirement 4)
                    val checkedUris = selectedUris.filter { it.value }.keys.toList()
                    val selectedCount = checkedUris.size
                    
                    if (selectedCount > 0) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Selection Info Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedCount item(s) selected",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = { selectedUris.clear() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Text("Deselect All", fontSize = 12.sp)
                                    }
                                }

                                // Row 1: Send to Source Folder & Share
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.sendFilesToSourceFolder(context, checkedUris)
                                            refreshCurrentView()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("To Source", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            FileUtils.shareFiles(context, checkedUris, "Share $selectedCount File(s)")
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Row 2: Rename (if 1 item selected) & Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (selectedCount == 1) {
                                        val targetUri = checkedUris.first()
                                        val targetName = rawCurrentFiles.firstOrNull { it.second == targetUri }?.first ?: ""
                                        OutlinedButton(
                                            onClick = {
                                                renameTarget = Pair(targetName, targetUri)
                                                renameText = targetName
                                                showRenameDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Rename", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.deleteCleanupFiles(context, checkedUris, selectedTab != 1)
                                            refreshCurrentView()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rename Dialog
        if (showRenameDialog && renameTarget != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename Item", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Enter new name for '${renameTarget?.first}':", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            label = { Text("New Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = renameTarget
                            if (target != null && renameText.isNotBlank()) {
                                viewModel.renameCleanupFile(context, target.second, renameText.trim())
                                refreshCurrentView()
                            }
                            showRenameDialog = false
                        }
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
