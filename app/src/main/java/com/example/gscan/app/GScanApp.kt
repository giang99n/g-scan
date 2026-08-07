package com.example.gscan.app

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gscan.feature.documents.presentation.DocumentsRoute
import com.example.gscan.feature.scanner.presentation.ScannerPlaceholderScreen

private object Route {
    const val DOCUMENTS = "documents"
    const val SCANNER = "scanner"
}

@Composable
fun GScanApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Route.DOCUMENTS,
    ) {
        composable(Route.DOCUMENTS) {
            DocumentsRoute(onScanClick = { navController.navigate(Route.SCANNER) })
        }
        composable(Route.SCANNER) {
            ScannerPlaceholderScreen(onBackClick = navController::navigateUp)
        }
    }
}
