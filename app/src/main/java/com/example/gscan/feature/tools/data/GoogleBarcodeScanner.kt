package com.example.gscan.feature.tools.data

import android.content.Context
import com.example.gscan.feature.tools.domain.BarcodeResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

class GoogleBarcodeScanner @javax.inject.Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) : com.example.gscan.feature.tools.domain.BarcodeScanner {
    override fun scan(onResult: (BarcodeResult) -> Unit, onCancelled: () -> Unit, onError: (String) -> Unit) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            onError("Google Play services chưa sẵn sàng. Hãy cài đặt hoặc cập nhật dịch vụ rồi thử lại.")
            return
        }
        try {
            val options = GmsBarcodeScannerOptions.Builder().enableAutoZoom().build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    val content = barcode.rawValue
                    if (content.isNullOrEmpty()) onError("Mã không chứa văn bản có thể hiển thị; mã nhị phân chưa được hỗ trợ.")
                    else if (content.length > 32768) onError("Nội dung mã quá dài để hiển thị và lưu.")
                    else onResult(barcode.toDomainResult(content))
                }
                .addOnCanceledListener(onCancelled)
                .addOnFailureListener { onError("Không mở được máy quét. Lần đầu cần mạng để tải module; hãy kiểm tra Google Play services và thử lại.") }
        } catch (_: Exception) {
            onError("Không khởi động được máy quét trên thiết bị này. Hãy thử lại.")
        }
    }
}

internal fun Barcode.toDomainResult(content: String = requireNotNull(rawValue)): BarcodeResult =
    BarcodeResult(content, formatLabel(format), typeLabel(valueType))

private fun formatLabel(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR"
    Barcode.FORMAT_CODE_128 -> "Code 128"
    Barcode.FORMAT_CODE_39 -> "Code 39"
    Barcode.FORMAT_CODE_93 -> "Code 93"
    Barcode.FORMAT_CODABAR -> "Codabar"
    Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "Aztec"
    else -> "Barcode"
}

private fun typeLabel(type: Int): String = when (type) {
    Barcode.TYPE_URL -> "Đường dẫn"
    Barcode.TYPE_WIFI -> "Wi-Fi"
    Barcode.TYPE_CONTACT_INFO -> "Liên hệ"
    Barcode.TYPE_EMAIL -> "Email"
    Barcode.TYPE_PHONE -> "Số điện thoại"
    Barcode.TYPE_SMS -> "SMS"
    Barcode.TYPE_GEO -> "Vị trí"
    Barcode.TYPE_CALENDAR_EVENT -> "Sự kiện"
    Barcode.TYPE_PRODUCT -> "Sản phẩm"
    Barcode.TYPE_ISBN -> "ISBN"
    else -> "Văn bản"
}
