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
import com.pmsuryaghar.docprocessor.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    // Activity Result Launchers for Directory trees
    val sourceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateSettings(settings.copy(sourceFolderUri = uri.toString()))
        }
    }

    val outputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateSettings(settings.copy(defaultOutputFolderUri = uri.toString()))
        }
    }

    val waMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.updateSettings(settings.copy(whatsappMediaFolderUri = uri.toString()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            // Folder Configurations Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Storage Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Source Folder Config
                    Text("Source WhatsApp Folder:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.sourceFolderUri.isEmpty()) "Not Configured" else Uri.parse(settings.sourceFolderUri).path ?: "Selected Directory",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Button(onClick = { sourceLauncher.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom WhatsApp Media Folder Config
                    Text("Custom WhatsApp Media Folder:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.whatsappMediaFolderUri.isEmpty()) "Not Configured (Tap Select to set folder)" else Uri.parse(settings.whatsappMediaFolderUri).path ?: "Selected Directory",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Button(onClick = { waMediaLauncher.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Output Folder Config
                    Text("Default Output Folder:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (settings.defaultOutputFolderUri.isEmpty()) "Not Configured" else Uri.parse(settings.defaultOutputFolderUri).path ?: "Selected Directory",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2
                        )
                        Button(onClick = { outputLauncher.launch(null) }) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select")
                        }
                    }
                }
            }

            // WhatsApp Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "WhatsApp Integration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.destinationWhatsAppNumber,
                        onValueChange = { viewModel.updateSettings(settings.copy(destinationWhatsAppNumber = it)) },
                        label = { Text("Destination WhatsApp Number") },
                        placeholder = { Text("e.g. 919876543210") },
                        helperText = { Text("Include country code (e.g. 91 for India)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Application Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Processing Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // AI Agent Selector (Gemini or ChatGPT)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Verification Agent:", fontWeight = FontWeight.Medium)
                            Text("Choose AI assistant to perform document analysis", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row {
                            FilterChip(
                                selected = settings.selectedAiAgent.equals("Gemini", ignoreCase = true),
                                onClick = { viewModel.updateSettings(settings.copy(selectedAiAgent = "Gemini")) },
                                label = { Text("Gemini") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = settings.selectedAiAgent.equals("ChatGPT", ignoreCase = true),
                                onClick = { viewModel.updateSettings(settings.copy(selectedAiAgent = "ChatGPT")) },
                                label = { Text("ChatGPT") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Language Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Verification Language:")
                        Row {
                            FilterChip(
                                selected = settings.language == "English",
                                onClick = { viewModel.updateSettings(settings.copy(language = "English")) },
                                label = { Text("English") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            FilterChip(
                                selected = settings.language == "Odia",
                                onClick = { viewModel.updateSettings(settings.copy(language = "Odia")) },
                                label = { Text("Odia") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Max PDF Size
                    OutlinedTextField(
                        value = settings.maxPdfSizeMb.toString(),
                        onValueChange = {
                            val parsed = it.toFloatOrNull()
                            if (parsed != null) {
                                viewModel.updateSettings(settings.copy(maxPdfSizeMb = parsed))
                            }
                        },
                        label = { Text("Maximum PDF File Size (MB)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ZIP format
                    OutlinedTextField(
                        value = settings.zipFilenameFormat,
                        onValueChange = { viewModel.updateSettings(settings.copy(zipFilenameFormat = it)) },
                        label = { Text("ZIP Filename Format") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Generate PDF Report toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Generate PDF Report", fontWeight = FontWeight.Medium)
                            Text("Create both DOCX and PDF verification reports", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.generatePdfReport,
                            onCheckedChange = { viewModel.updateSettings(settings.copy(generatePdfReport = it)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.updateSettings(settings.copy(lastProcessingTimestamp = 0L))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Last Execution Time")
                    }
                }
            }

            // Quick Utility Portals Configuration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Verification Portal URLs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.aadhaarPortalUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(aadhaarPortalUrl = it)) },
                        label = { Text("Aadhaar Verification Web Portal URL") },
                        placeholder = { Text("https://tathya.uidai.gov.in/access/login?role=resident") },
                        helperText = { Text("URL launched when tapping the Aadhaar card link on the Dashboard") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.electricityPortalUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(electricityPortalUrl = it)) },
                        label = { Text("Electricity Bill Web Portal URL") },
                        placeholder = { Text("https://mytatapowerplus.tatapower.com/#/offerings") },
                        helperText = { Text("URL launched when tapping the TPCODL / Electricity bill link on the Dashboard") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.landRecordPortalUrl,
                        onValueChange = { viewModel.updateSettings(settings.copy(landRecordPortalUrl = it)) },
                        label = { Text("Land Record Web Portal URL") },
                        placeholder = { Text("https://bhulekh.ori.nic.in/") },
                        helperText = { Text("URL launched when tapping the Land Record link on the Dashboard (default: Odisha Bhulekh)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Gemini Prompt Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Default Gemini Prompt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = settings.defaultGeminiPrompt,
                        onValueChange = { viewModel.updateSettings(settings.copy(defaultGeminiPrompt = it)) },
                        label = { Text("Verification Prompt") },
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        maxLines = 15
                    )
                }
            }
        }
    }
}

// Simple Helper Extension to show supporting text under OutlinedTextFields
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)? = null,
    helperText: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE
) {
    Column(modifier = modifier) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth()
        )
        if (helperText != null) {
            Spacer(modifier = Modifier.height(2.dp))
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(value = MaterialTheme.typography.bodySmall) {
                    helperText()
                }
            }
        }
    }
}
