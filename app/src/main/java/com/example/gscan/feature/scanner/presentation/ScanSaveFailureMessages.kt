package com.example.gscan.feature.scanner.presentation

import com.example.gscan.feature.scanner.domain.model.ScanSaveFailure

internal enum class SaveInputKind {
    SCAN,
    IMPORT,
}

internal fun ScanSaveFailure.toUserMessage(inputKind: SaveInputKind): String = when (this) {
    ScanSaveFailure.NO_PAGES -> when (inputKind) {
        SaveInputKind.SCAN -> "Không có trang nào để lưu."
        SaveInputKind.IMPORT -> "Không có ảnh nào để lưu."
    }
    ScanSaveFailure.TOO_MANY_PAGES -> "Mỗi tài liệu chỉ được chứa tối đa 100 trang."
    ScanSaveFailure.SOURCE_UNAVAILABLE -> when (inputKind) {
        SaveInputKind.SCAN -> "Không còn quyền đọc kết quả scan. Vui lòng scan lại."
        SaveInputKind.IMPORT -> "Không còn quyền đọc ảnh nguồn. Vui lòng chọn ảnh lại."
    }
    ScanSaveFailure.STORAGE_FULL -> "Thiết bị không đủ dung lượng để lưu tài liệu."
    ScanSaveFailure.INVALID_IMAGE -> when (inputKind) {
        SaveInputKind.SCAN -> "Một trang scan bị lỗi hoặc không đúng định dạng ảnh."
        SaveInputKind.IMPORT -> "Một ảnh bị lỗi hoặc không thuộc định dạng được hỗ trợ."
    }
    ScanSaveFailure.DATABASE_ERROR ->
        "Không thể ghi tài liệu vào thư viện. Các file vừa tạo đã được dọn dẹp."
    ScanSaveFailure.CLEANUP_FAILED ->
        "Lưu tài liệu thất bại và chưa thể dọn hết file tạm. GScan sẽ thử dọn lại khi mở app."
    ScanSaveFailure.UNKNOWN -> "Không thể lưu tài liệu. Vui lòng thử lại."
}
