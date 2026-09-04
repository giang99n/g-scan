package com.example.gscan.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gscan.feature.backup.presentation.BackupScreen
import com.example.gscan.feature.documents.presentation.DocumentsRoute
import com.example.gscan.feature.documents.presentation.DOCUMENT_ID_ARGUMENT
import com.example.gscan.feature.documents.presentation.DocumentDetailRoute
import com.example.gscan.feature.editor.presentation.SignatureScreen
import com.example.gscan.feature.export.presentation.PdfToolsScreen
import com.example.gscan.feature.home.presentation.HomeFeature
import com.example.gscan.feature.home.presentation.HomeScreen
import com.example.gscan.feature.ocr.presentation.OcrRoute
import com.example.gscan.feature.scanner.presentation.ImportScreen
import com.example.gscan.feature.scanner.presentation.ScannerScreen
import com.example.gscan.feature.security.presentation.SecurityScreen
import com.example.gscan.feature.tools.presentation.QrBarcodeScreen

private object Route {
    const val HOME = "home"
    const val DOCUMENTS = "documents"
    const val DOCUMENT_DETAIL = "documents/{$DOCUMENT_ID_ARGUMENT}"
    const val SCANNER = "scanner"
    const val IMPORT = "import"
    const val OCR = "ocr"
    const val DOCUMENT_OCR = "documents/{$DOCUMENT_ID_ARGUMENT}/ocr"
    const val PDF_TOOLS = "pdf_tools"
    const val PDF_EXPORT = "documents/{$DOCUMENT_ID_ARGUMENT}/export"
    const val SIGNATURE = "signature"
    const val QR_BARCODE = "qr_barcode"
    const val SECURITY = "security"
    const val BACKUP = "backup"
}

private fun HomeFeature.toRoute(): String = when (this) {
    HomeFeature.SCANNER -> Route.SCANNER
    HomeFeature.DOCUMENTS -> Route.DOCUMENTS
    HomeFeature.IMPORT -> Route.IMPORT
    HomeFeature.OCR -> Route.OCR
    HomeFeature.PDF_TOOLS -> Route.PDF_TOOLS
    HomeFeature.SIGNATURE -> Route.SIGNATURE
    HomeFeature.QR_BARCODE -> Route.QR_BARCODE
    HomeFeature.SECURITY -> Route.SECURITY
    HomeFeature.BACKUP -> Route.BACKUP
}

@Composable
fun GScanApp(
    sharedPdfUri: String? = null,
    onSharedPdfConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    LaunchedEffect(sharedPdfUri) {
        if (sharedPdfUri != null) {
            navController.navigate(Route.IMPORT) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Route.HOME,
    ) {
        composable(Route.HOME) {
            HomeScreen(onFeatureClick = { navController.navigate(it.toRoute()) })
        }
        composable(Route.DOCUMENTS) {
            DocumentsRoute(
                onBackClick = navController::navigateUp,
                onScanClick = { navController.navigate(Route.SCANNER) },
                onDocumentClick = { documentId ->
                    navController.navigate("documents/$documentId")
                },
            )
        }
        composable(Route.DOCUMENT_DETAIL) {
            DocumentDetailRoute(
                onBackClick = navController::navigateUp,
                onExportClick = { documentId ->
                    navController.navigate("documents/$documentId/export")
                },
                onOcrClick = { documentId ->
                    navController.navigate("documents/$documentId/ocr")
                },
            )
        }
        composable(Route.SCANNER) {
            ScannerScreen(
                onBackClick = navController::navigateUp,
                onDocumentSaved = {
                    navController.navigate(Route.DOCUMENTS) {
                        popUpTo(Route.SCANNER) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Route.IMPORT) {
            ImportScreen(
                initialPdfUri = sharedPdfUri,
                onInitialPdfConsumed = onSharedPdfConsumed,
                onBackClick = navController::navigateUp,
                onDocumentSaved = {
                    navController.navigate(Route.DOCUMENTS) {
                        popUpTo(Route.IMPORT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Route.OCR) {
            DocumentsRoute(
                title = "OCR & tìm kiếm",
                onBackClick = navController::navigateUp,
                onScanClick = { navController.navigate(Route.SCANNER) },
                onDocumentClick = { documentId ->
                    navController.navigate("documents/$documentId/ocr")
                },
            )
        }
        composable(Route.DOCUMENT_OCR) {
            OcrRoute(onBackClick = navController::navigateUp)
        }
        composable(Route.PDF_TOOLS) {
            PdfToolsScreen(
                onBackClick = navController::navigateUp,
                onChooseDocument = { navController.navigate(Route.DOCUMENTS) },
            )
        }
        composable(Route.PDF_EXPORT) {
            PdfToolsScreen(
                onBackClick = navController::navigateUp,
                onChooseDocument = { navController.navigate(Route.DOCUMENTS) },
            )
        }
        composable(Route.SIGNATURE) {
            SignatureScreen(onBackClick = navController::navigateUp)
        }
        composable(Route.QR_BARCODE) {
            QrBarcodeScreen(onBackClick = navController::navigateUp)
        }
        composable(Route.SECURITY) {
            SecurityScreen(onBackClick = navController::navigateUp)
        }
        composable(Route.BACKUP) {
            BackupScreen(onBackClick = navController::navigateUp)
        }
    }
}
