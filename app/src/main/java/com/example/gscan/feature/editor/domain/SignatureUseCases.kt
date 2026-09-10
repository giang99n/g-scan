package com.example.gscan.feature.editor.domain

import javax.inject.Inject

class SignatureUseCases @Inject constructor(private val repository: SignatureRepository) {
    fun templates() = repository.observeTemplates()
    suspend fun saveTemplate(name: String, ink: Ink) {
        validateInk(ink)
        repository.saveTemplate(name, ink)
    }
    suspend fun deleteTemplate(id: String) = repository.deleteTemplate(id)
    suspend fun savePage(documentId: String, pageId: String, rotation: Int, ink: Ink) {
        validateInk(ink)
        repository.savePage(documentId, pageId, rotation, ink)
    }
}
