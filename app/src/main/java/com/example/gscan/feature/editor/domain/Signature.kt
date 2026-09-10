package com.example.gscan.feature.editor.domain

import kotlinx.coroutines.flow.Flow

data class InkPoint(val x: Float, val y: Float)
typealias Ink = List<List<InkPoint>>
data class SignatureTemplate(val id: String, val name: String, val ink: Ink)

fun InkPoint.rotated(degrees: Int): InkPoint = when ((degrees % 360 + 360) % 360) {
    90 -> InkPoint(1f - y, x)
    180 -> InkPoint(1f - x, 1f - y)
    270 -> InkPoint(y, 1f - x)
    else -> this
}

fun validateInk(ink: Ink) {
    require(ink.size <= 100 && ink.sumOf { it.size } <= 8000) { "Chữ ký quá nhiều nét. Hãy vẽ gọn hơn." }
    require(ink.all { stroke -> stroke.isNotEmpty() && stroke.all {
        it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f
    } }) { "Chữ ký không hợp lệ." }
}

interface SignatureRepository {
    fun observeTemplates(): Flow<List<SignatureTemplate>>
    suspend fun saveTemplate(name: String, ink: Ink)
    suspend fun deleteTemplate(id: String)
    suspend fun savePage(documentId: String, pageId: String, rotation: Int, ink: Ink)
}
