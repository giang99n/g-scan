package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.MAX_DOCUMENT_TITLE_LENGTH
import com.example.gscan.feature.documents.domain.model.DocumentPageSelection
import com.example.gscan.feature.documents.domain.model.MAX_PAGES_PER_DOCUMENT
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject

class ComposeDocumentUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(
        title: String,
        selections: List<DocumentPageSelection>,
        onProgress: (Int, Int) -> Unit,
    ): String {
        val normalized = title.map { if (it.isISOControl()) ' ' else it }.joinToString("")
            .trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotEmpty()) { "Hãy nhập tên tài liệu mới." }
        require(normalized.length <= MAX_DOCUMENT_TITLE_LENGTH) { "Tên tài liệu tối đa 120 ký tự." }
        require(selections.isNotEmpty() && selections.all { it.pageIds.isNotEmpty() }) {
            "Hãy chọn trang cần tạo tài liệu."
        }
        require(selections.sumOf { it.pageIds.size } <= MAX_PAGES_PER_DOCUMENT) {
            "Tài liệu mới được chứa tối đa 100 trang."
        }
        return repository.composeDocument(normalized, selections, onProgress)
    }
}

/** Page numbers are one-based; preserve the order entered by the user. */
fun parsePageSelection(input: String, pageCount: Int): List<Int> {
    require(pageCount in 1..MAX_PAGES_PER_DOCUMENT) { "Tài liệu không có trang hợp lệ." }
    if (input.isBlank()) return (1..pageCount).toList()
    require(input.length <= 600) { "Danh sách trang quá dài." }
    val pages = linkedSetOf<Int>()
    input.split(',').forEach { part ->
        val match = Regex("\\s*(\\d+)\\s*(?:-\\s*(\\d+)\\s*)?").matchEntire(part)
        requireNotNull(match) { "Nhập trang theo mẫu 1, 3-5." }
        val start = match.groupValues[1].toIntOrNull() ?: 0
        val endText = match.groupValues[2]
        val end = if (endText.isEmpty()) start else endText.toIntOrNull() ?: 0
        require(start in 1..pageCount && end in start..pageCount) {
            "Trang phải nằm trong 1–$pageCount; khoảng trang phải tăng dần."
        }
        pages.addAll(start..end)
    }
    return pages.toList()
}
