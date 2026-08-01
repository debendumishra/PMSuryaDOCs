package com.pmsuryaghar.docprocessor.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState),
        ) {
            
            SettingsSectionHeader("Storage Folders")
            
            SettingsFolderItem(
                title = "Source WhatsApp Folder",
                uriString = settings.sourceFolderUri,
                onClick = { sourceLauncher.launch(null) }
            )
            
            SettingsFolderItem(
                title = "Custom WhatsApp Media Folder",
                uriString = settings.whatsappMediaFolderUri,
                onClick = { waMediaLauncher.launch(null) },
                fallbackSubtitle = "Not Configured (Tap to set folder)"
            )
            
            SettingsFolderItem(
                title = "Default Output Folder",
                uriString = settings.defaultOutputFolderUri,
                onClick = { outputLauncher.launch(null) }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSectionHeader("WhatsApp Integration")
            
            SettingsTextInputItem(
                title = "Destination WhatsApp Number",
                subtitle = "Include country code (e.g. 91 for India)",
                value = settings.destinationWhatsAppNumber,
                onValueChange = { viewModel.updateSettings(settings.copy(destinationWhatsAppNumber = it)) },
                placeholder = "e.g. 919876543210"
            )

            SettingsTextInputItem(
                title = "TPCODL WhatsApp Number",
                subtitle = "For opening TPCODL via WhatsApp",
                value = settings.tpcodlWhatsappNumber,
                onValueChange = { viewModel.updateSettings(settings.copy(tpcodlWhatsappNumber = it)) },
                placeholder = "e.g. 919876543210"
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSectionHeader("Processing Preferences")
            
            ListItem(
                headlineContent = { Text("AI Verification Agent") },
                supportingContent = { Text("Choose AI assistant to perform document analysis") },
                trailingContent = {
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
            )
            
            ListItem(
                headlineContent = { Text("Verification Language") },
                trailingContent = {
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
            )
            
            SettingsTextInputItem(
                title = "Maximum PDF File Size (MB)",
                value = settings.maxPdfSizeMb.toString(),
                onValueChange = { 
                    val parsed = it.toFloatOrNull()
                    if (parsed != null) {
                        viewModel.updateSettings(settings.copy(maxPdfSizeMb = parsed))
                    }
                }
            )
            
            SettingsTextInputItem(
                title = "ZIP Filename Format",
                value = settings.zipFilenameFormat,
                onValueChange = { viewModel.updateSettings(settings.copy(zipFilenameFormat = it)) }
            )
            
            ListItem(
                headlineContent = { Text("Generate PDF Report") },
                supportingContent = { Text("Create both DOCX and PDF verification reports") },
                trailingContent = {
                    Switch(
                        checked = settings.generatePdfReport,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(generatePdfReport = it)) }
                    )
                },
                modifier = Modifier.clickable { viewModel.updateSettings(settings.copy(generatePdfReport = !settings.generatePdfReport)) }
            )
            
            ListItem(
                headlineContent = { Text("Reset Last Execution Time", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Reset the timestamp to process all files again") },
                modifier = Modifier.clickable { viewModel.updateSettings(settings.copy(lastProcessingTimestamp = 0L)) }
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSectionHeader("Verification Portal URLs")
            
            SettingsTextInputItem(
                title = "Aadhaar Verification Web Portal URL",
                subtitle = "URL launched when tapping the Aadhaar card link on the Dashboard",
                value = settings.aadhaarPortalUrl,
                onValueChange = { viewModel.updateSettings(settings.copy(aadhaarPortalUrl = it)) },
                placeholder = "https://tathya.uidai.gov.in/..."
            )
            
            SettingsTextInputItem(
                title = "Electricity Bill Web Portal URL",
                subtitle = "URL launched when tapping the TPCODL link on the Dashboard",
                value = settings.electricityPortalUrl,
                onValueChange = { viewModel.updateSettings(settings.copy(electricityPortalUrl = it)) },
                placeholder = "https://mytatapowerplus.tatapower.com/..."
            )
            
            SettingsTextInputItem(
                title = "Land Record Web Portal URL",
                subtitle = "URL launched when tapping the Land Record link on the Dashboard",
                value = settings.landRecordPortalUrl,
                onValueChange = { viewModel.updateSettings(settings.copy(landRecordPortalUrl = it)) },
                placeholder = "https://bhulekh.ori.nic.in/"
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            SettingsSectionHeader("Default Gemini Prompt")
            
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = settings.defaultGeminiPrompt,
                    onValueChange = { viewModel.updateSettings(settings.copy(defaultGeminiPrompt = it)) },
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    maxLines = 15
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsFolderItem(
    title: String,
    uriString: String,
    onClick: () -> Unit,
    fallbackSubtitle: String = "Not Configured"
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { 
            Text(if (uriString.isEmpty()) fallbackSubtitle else Uri.parse(uriString).path ?: "Selected Directory") 
        },
        leadingContent = {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsTextInputItem(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    subtitle: String? = null,
    placeholder: String? = null
) {
    var localValue by remember { mutableStateOf(value) }
    
    // Sync external changes (e.g. initial load) only if we aren't currently editing
    // Or just update if external value changes significantly
    LaunchedEffect(value) {
        if (value != localValue && localValue.isEmpty()) {
            localValue = value
        }
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = localValue,
            onValueChange = { 
                localValue = it
                onValueChange(it) 
            },
            label = { Text(title) },
            placeholder = { if (placeholder != null) Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
