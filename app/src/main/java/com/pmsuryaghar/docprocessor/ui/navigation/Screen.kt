package com.pmsuryaghar.docprocessor.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object Processing : Screen("processing")
    data object FolderReview : Screen("folder_review")
    data object History : Screen("history")
    data object FileCleanup : Screen("file_cleanup")
}
