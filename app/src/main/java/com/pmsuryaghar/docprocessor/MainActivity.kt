package com.pmsuryaghar.docprocessor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pmsuryaghar.docprocessor.ui.navigation.Screen
import com.pmsuryaghar.docprocessor.ui.screens.*
import com.pmsuryaghar.docprocessor.ui.theme.PMSuryaDOCsTheme
import com.pmsuryaghar.docprocessor.ui.viewmodel.MainViewModel
import com.pmsuryaghar.docprocessor.ui.viewmodel.ProcessingState
import com.pmsuryaghar.docprocessor.ui.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent)

        setContent {
            PMSuryaDOCsTheme {
                val navController = rememberNavController()

                // Observe ViewModel processing state to handle transition when Gemini shared intent arrives
                val stateFlow = mainViewModel.processingState
                LaunchedEffect(stateFlow) {
                    stateFlow.collect { state ->
                        if (state == ProcessingState.FOLDER_REVIEW) {
                            navController.navigate(Screen.FolderReview.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                }

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == Screen.Home.route,
                                onClick = {
                                    if (currentRoute != Screen.Home.route) {
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard", fontSize = 11.sp, fontWeight = if (currentRoute == Screen.Home.route) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == Screen.FileCleanup.route,
                                onClick = {
                                    if (currentRoute != Screen.FileCleanup.route) {
                                        navController.navigate(Screen.FileCleanup.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.PendingActions, contentDescription = "Storage") },
                                label = { Text("Storage", fontSize = 11.sp, fontWeight = if (currentRoute == Screen.FileCleanup.route) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == Screen.History.route,
                                onClick = {
                                    if (currentRoute != Screen.History.route) {
                                        navController.navigate(Screen.History.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                label = { Text("History", fontSize = 11.sp, fontWeight = if (currentRoute == Screen.History.route) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) }
                            )
                            NavigationBarItem(
                                selected = currentRoute == Screen.Settings.route,
                                onClick = {
                                    if (currentRoute != Screen.Settings.route) {
                                        navController.navigate(Screen.Settings.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings", fontSize = 11.sp, fontWeight = if (currentRoute == Screen.Settings.route) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal) }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                viewModel = mainViewModel,
                                onStartProcessingClick = {
                                    mainViewModel.startProcessing(this@MainActivity)
                                    navController.navigate(Screen.Processing.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onNavigateToHistory = {
                                    navController.navigate(Screen.History.route)
                                },
                                onNavigateToCleanup = {
                                    navController.navigate(Screen.FileCleanup.route)
                                }
                            )
                        }

                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.History.route) {
                            HistoryScreen(
                                viewModel = mainViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(Screen.Processing.route) {
                            ProcessingScreen(
                                viewModel = mainViewModel,
                                onBackToHome = {
                                    mainViewModel.resetState()
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                    }
                                },
                                onNavigateToReview = {
                                    navController.navigate(Screen.FolderReview.route) {
                                        popUpTo(Screen.Processing.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.FolderReview.route) {
                            FolderReviewScreen(
                                viewModel = mainViewModel,
                                onBackClick = {
                                    mainViewModel.resetState()
                                    navController.popBackStack()
                                },
                                onContinueClick = {
                                    mainViewModel.saveAndShare(this@MainActivity)
                                    navController.navigate(Screen.Processing.route) {
                                        popUpTo(Screen.FolderReview.route) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Screen.FileCleanup.route) {
                            FileCleanupScreen(
                                viewModel = mainViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                    if (sharedText.isNotEmpty()) {
                        mainViewModel.onGeminiResponseReceived(sharedText)
                    }
                } else if (intent.type?.startsWith("image/") == true || intent.type == "application/pdf") {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) {
                        mainViewModel.onFilesShared(this@MainActivity, listOf(uri))
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (intent.type?.startsWith("image/") == true || intent.type == "application/pdf") {
                    val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uris != null) {
                        mainViewModel.onFilesShared(this@MainActivity, uris)
                    }
                }
            }
        }
    }
}
