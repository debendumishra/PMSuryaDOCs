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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        mainViewModel.loadCleanupFiles(this@MainActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        
        checkAndRequestPermissions()
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

                val appSettings by mainViewModel.settings.collectAsState()
                var isAppUnlockedSession by remember { mutableStateOf(false) }
                val isAppUnlocked = appSettings.isAppUnlocked || isAppUnlockedSession

                if (!isAppUnlocked) {
                    LoginScreen(onUnlock = { 
                        isAppUnlockedSession = true 
                        mainViewModel.setAppUnlocked()
                    })
                } else {
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
                        mainViewModel.onGeminiResponseReceived(this, sharedText)
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        // On Android 11+ (API 30+), request All Files Access for reading WhatsApp app storage folders directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Timber.e(e2, "Could not open manage all files access permission screen")
                    }
                }
            }
        }
    }
}
