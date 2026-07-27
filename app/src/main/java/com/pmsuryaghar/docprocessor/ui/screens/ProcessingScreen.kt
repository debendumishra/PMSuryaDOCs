package com.pmsuryaghar.docprocessor.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pmsuryaghar.docprocessor.ui.viewmodel.MainViewModel
import com.pmsuryaghar.docprocessor.ui.viewmodel.ProcessingState

@Composable
fun ProcessingScreen(
    viewModel: MainViewModel,
    onBackToHome: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    val processingState by viewModel.processingState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    var pastedText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Monitor for transition to FOLDER_REVIEW state
    LaunchedEffect(processingState) {
        if (processingState == ProcessingState.FOLDER_REVIEW) {
            onNavigateToReview()
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Title
                Text(
                    "Processing Documents",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Render based on active state
                when (processingState) {
                    ProcessingState.SCANNING,
                    ProcessingState.ORGANIZING,
                    ProcessingState.GENERATING_PDFS,
                    ProcessingState.LAUNCHING_GEMINI,
                    ProcessingState.EXTRACTING_DETAILS,
                    ProcessingState.SAVING_FILES -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(72.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    ProcessingState.WAITING_GEMINI -> {
                        val agentName = settings.selectedAiAgent.ifEmpty { "Gemini" }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$agentName Response Required",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "We have opened the $agentName application with your documents and the prompt. Please allow $agentName to process, then:\n\n" +
                                            "1. Copy the $agentName response.\n" +
                                            "2. Paste it in the box below, OR click 'Share' in the $agentName app and choose this application.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Text Area for manual paste
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            label = { Text("Paste $agentName Response") },
                            placeholder = { Text("Paste the text response generated by $agentName here...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            maxLines = 15
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Clipboard Helper Button
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        val text = clip.getItemAt(0).text
                                        if (text != null) {
                                            pastedText = text.toString()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Paste Clipboard")
                            }

                            // Submit Button
                            Button(
                                onClick = {
                                    if (pastedText.trim().isNotEmpty()) {
                                        viewModel.onGeminiResponseReceived(pastedText)
                                    }
                                },
                                enabled = pastedText.trim().isNotEmpty(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Parse Response")
                            }
                        }
                    }

                    ProcessingState.COMPLETED -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                        Text(
                            "Processing Completed Successfully!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Files have been saved to your output folder and shared on WhatsApp.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onBackToHome, modifier = Modifier.fillMaxWidth()) {
                            Text("Done")
                        }
                    }

                    ProcessingState.ERROR -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(80.dp)
                        )
                        Text(
                            "An Error Occurred",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(onClick = onBackToHome, modifier = Modifier.weight(1f)) {
                                Text("Back Home")
                            }
                            Button(
                                onClick = { viewModel.startProcessing(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
