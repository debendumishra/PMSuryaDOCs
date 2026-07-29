package com.pmsuryaghar.docprocessor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
    val whatsappMediaFiles by viewModel.cleanupWhatsappMediaFiles.collectAsState()
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
    
    // Determine current files list based on selected tab and subfolder state (3 tabs: Inbox, Outbox, WA)
    val MAX_DISPLAY = 100
    val rawCurrentFiles = when {
        currentSubfolder != null -> activeSubfolderFiles.take(MAX_DISPLAY)
        selectedTab == 0 -> sourceFiles
        selectedTab == 1 -> destFiles
        else -> whatsappMediaFiles.take(MAX_DISPLAY)
    }
    val totalCurrentCount = when {
        currentSubfolder != null -> activeSubfolderFiles.size
        selectedTab == 0 -> sourceFiles.size
        selectedTab == 1 -> destFiles.size
        else -> whatsappMediaFiles.size
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
                        Text(
                            text = "Storage & File Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (currentSubfolder != null) "Folder > ${currentSubfolder?.first}"
                                   else if (selectedTab == 0) "Inbox (Source)"
                                   else if (selectedTab == 1) "Outbox (Destination)"
                                   else "WhatsApp Media (WA)",
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
                actions = {
                    // Small compact Refresh button in TopAppBar for WA tab
                    if (selectedTab == 2 && currentSubfolder == null) {
                        IconButton(
                            onClick = { viewModel.refreshCustomWhatsappFolder(context) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh WA Folder",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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
            // 3 Tabs Header: Inbox (Source), Outbox (Destination), WA with compact refresh icon
            if (currentSubfolder == null) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    // Tab 0: Inbox
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            selectedTab = 0
                            currentSubfolder = null
                        },
                        text = {
                            Text(
                                text = "Inbox (${sourceFiles.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )

                    // Tab 1: Outbox
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            currentSubfolder = null
                        },
                        text = {
                            Text(
                                text = "Outbox (${destFiles.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    )

                    // Tab 2: WA with small Refresh Icon
                    Tab(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            currentSubfolder = null
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "WA (${whatsappMediaFiles.size})",
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Sync",
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable { viewModel.refreshCustomWhatsappFolder(context) },
                                    tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Root > ${currentSubfolder?.first}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { currentSubfolder = null },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Up Directory", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Sync Status Indicator Banner (Compact height, shown only when status is non-empty)
            if (currentSubfolder == null && selectedTab == 2 && syncStatus.isNotEmpty()) {
                Surface(
                    color = if (syncStatus.contains("failed", ignoreCase = true) || syncStatus.contains("not set", ignoreCase = true) || syncStatus.contains("required", ignoreCase = true))
                        Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (syncStatus.contains("failed", ignoreCase = true) || syncStatus.contains("not set", ignoreCase = true) || syncStatus.contains("required", ignoreCase = true))
                                Color(0xFF991B1B) else Color(0xFF166534),
                            modifier = Modifier.weight(1f)
                        )
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No files found in this folder.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (currentSubfolder != null) "Subfolder is empty."
                                   else if (selectedTab == 0) "Inbox (Source folder) is empty or not set in Settings."
                                   else if (selectedTab == 1) "Outbox (Destination folder) is empty."
                                   else "Custom WA folder is empty.\nTap the refresh icon 🔄 on the tab to fetch recent WA files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    // Select All Row
                    val allSelected = rawCurrentFiles.isNotEmpty() && rawCurrentFiles.all { selectedUris[it.second] == true }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Select All (${rawCurrentFiles.size} items)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Checkbox(
                                checked = allSelected,
                                onCheckedChange = { isChecked ->
                                    rawCurrentFiles.forEach { selectedUris[it.second] = isChecked }
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Subfolder loading indicator
                    if (isLoadingSubfolder) {
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // File count badge
                    if (totalCurrentCount > MAX_DISPLAY) {
                        Text(
                            text = "Showing $MAX_DISPLAY of $totalCurrentCount items (newest first)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Lazy List of Files/Folders
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(rawCurrentFiles, key = { it.second.toString() }) { (name, uri, isDirectory) ->
                            var showItemMenu by remember { mutableStateOf(false) }

                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDirectory) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f) 
                                                      else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (selectedUris[uri] == true) MaterialTheme.colorScheme.primary 
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isDirectory) {
                                            currentSubfolder = Pair(name, uri)
                                        } else {
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
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedUris[uri] == true,
                                        onCheckedChange = { isChecked ->
                                            selectedUris[uri] = isChecked
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    
                                    // Colored File/Folder Icon Badge
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isDirectory) Color(0xFFFEF3C7)
                                                else if (name.endsWith(".pdf", true)) Color(0xFFFEE2E2)
                                                else if (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true)) Color(0xFFDCFCE7)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = if (isDirectory) Icons.Default.Folder 
                                                               else if (name.endsWith(".pdf", true)) Icons.Default.PictureAsPdf
                                                               else if (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true)) Icons.Default.Image
                                                               else Icons.Default.Description,
                                                contentDescription = null,
                                                tint = if (isDirectory) Color(0xFFD97706)
                                                       else if (name.endsWith(".pdf", true)) Color(0xFFDC2626)
                                                       else if (name.endsWith(".jpg", true) || name.endsWith(".png", true) || name.endsWith(".jpeg", true)) Color(0xFF16A34A)
                                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isDirectory) FontWeight.Bold else FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isDirectory) "Directory • Tap to open" else "File • Tap to view",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Item Options Menu
                                    Box {
                                        IconButton(
                                            onClick = { showItemMenu = true },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(16.dp))
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
                                                text = { Text("Send to Inbox") },
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
                    
                    // Selected Items File Operations Toolbar — Docked compact bottom toolbar
                    val checkedUris = selectedUris.filter { it.value }.keys.toList()
                    val selectedCount = checkedUris.size
                    
                    if (selectedCount > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Selection Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = "$selectedCount",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                            )
                                        }
                                        Text(
                                            text = "selected",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = { selectedUris.clear() },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Deselect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Action Buttons Row: Compact single/dual row layout
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.sendFilesToSourceFolder(context, checkedUris)
                                            refreshCurrentView()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Input, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("To Inbox", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            FileUtils.shareFiles(context, checkedUris, "Share $selectedCount File(s)")
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (selectedCount == 1) {
                                        val targetUri = checkedUris.first()
                                        val targetName = rawCurrentFiles.firstOrNull { it.second == targetUri }?.first ?: ""
                                        OutlinedButton(
                                            onClick = {
                                                renameTarget = Pair(targetName, targetUri)
                                                renameText = targetName
                                                showRenameDialog = true
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Rename", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.deleteCleanupFiles(context, checkedUris, selectedTab != 1)
                                            refreshCurrentView()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
