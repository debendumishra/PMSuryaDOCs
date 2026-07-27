package com.pmsuryaghar.docprocessor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pmsuryaghar.docprocessor.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderReviewScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val context = LocalContext.current
    val detectedName by viewModel.detectedName.collectAsState()
    val detectedMobile by viewModel.detectedMobile.collectAsState()
    val proposedFolderName by viewModel.proposedFolderName.collectAsState()
    val selectedOutputLocationUri by viewModel.selectedOutputLocationUri.collectAsState()
    val scrollState = rememberScrollState()

    var nameInput by remember { mutableStateOf("") }
    var mobileInput by remember { mutableStateOf("") }
    var folderNameInput by remember { mutableStateOf("") }

    // Sync input states when viewmodel updates
    LaunchedEffect(detectedName, detectedMobile, proposedFolderName) {
        nameInput = detectedName
        mobileInput = detectedMobile
        folderNameInput = proposedFolderName
    }

    // Trigger update in viewmodel whenever inputs modify locally
    val updateInputs = {
        viewModel.updateFolderDetails(nameInput, mobileInput, folderNameInput)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateOutputLocation(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Output Folder Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Review and confirm the customer folder details before saving.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Input Fields Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Extracted Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            folderNameInput = "${it.trim()}_${mobileInput.trim()}"
                            updateInputs()
                        },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = mobileInput,
                        onValueChange = {
                            mobileInput = it
                            folderNameInput = "${nameInput.trim()}_${it.trim()}"
                            updateInputs()
                        },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = {
                            folderNameInput = it
                            updateInputs()
                        },
                        label = { Text("Proposed Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Target Location Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Output Location Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Selected Output Base Folder:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (selectedOutputLocationUri.isEmpty()) "Not Selected" else Uri.parse(selectedOutputLocationUri).path ?: "Selected base directory",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { folderLauncher.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change")
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Complete Directory Path Preview:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val basePath = if (selectedOutputLocationUri.isEmpty()) "Documents/PM_Surya_Ghar" else Uri.parse(selectedOutputLocationUri).path ?: "selected_folder"
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$basePath/$folderNameInput/",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }

            val existingFiles by viewModel.existingFiles.collectAsState()
            
            LaunchedEffect(selectedOutputLocationUri, folderNameInput) {
                viewModel.refreshExistingFiles(context)
            }

            val selectedFileUris = remember { mutableStateMapOf<Uri, Boolean>() }

            // Sync selections when files reload
            LaunchedEffect(existingFiles) {
                selectedFileUris.clear()
                existingFiles.forEach { selectedFileUris[it.second] = false }
            }

            if (selectedOutputLocationUri.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Directory Cleanup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (existingFiles.isEmpty()) {
                            Text(
                                "No existing files in output directory.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Found ${existingFiles.size} file(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                val allSelected = existingFiles.isNotEmpty() && existingFiles.all { selectedFileUris[it.second] == true }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Select All", style = MaterialTheme.typography.bodySmall)
                                    Checkbox(
                                        checked = allSelected,
                                        onCheckedChange = { isChecked ->
                                            existingFiles.forEach { selectedFileUris[it.second] = isChecked }
                                        }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                existingFiles.forEach { (fileName, fileUri) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = selectedFileUris[fileUri] == true,
                                            onCheckedChange = { isChecked ->
                                                selectedFileUris[fileUri] = isChecked
                                            }
                                        )
                                        Text(
                                            text = fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val selectedCount = selectedFileUris.filter { it.value }.size
                            Button(
                                onClick = {
                                    val toDelete = selectedFileUris.filter { it.value }.keys.toList()
                                    viewModel.deleteExistingFiles(context, toDelete)
                                },
                                enabled = selectedCount > 0,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Delete Selected ($selectedCount)")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onContinueClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save and Share ZIP", fontWeight = FontWeight.Bold)
            }
        }
    }
}
