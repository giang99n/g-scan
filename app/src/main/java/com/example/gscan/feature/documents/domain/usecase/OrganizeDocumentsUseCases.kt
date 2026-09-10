package com.example.gscan.feature.documents.domain.usecase

import com.example.gscan.feature.documents.domain.model.DocumentFolder
import com.example.gscan.feature.documents.domain.model.DocumentTag
import com.example.gscan.feature.documents.domain.repository.DocumentRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveFoldersUseCase @Inject constructor(private val repository: DocumentRepository) {
    operator fun invoke(): Flow<List<DocumentFolder>> = repository.observeFolders()
}

class ObserveTagsUseCase @Inject constructor(private val repository: DocumentRepository) {
    operator fun invoke(): Flow<List<DocumentTag>> = repository.observeTags()
}

class SetFavoriteUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(ids: Set<String>, favorite: Boolean) = repository.setFavorite(ids, favorite)
}

class MoveDocumentsToFolderUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(ids: Set<String>, folderId: String?) = repository.moveToFolder(ids, folderId)
}

class SetDocumentTagsUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(ids: Set<String>, addedTagIds: Set<String>, removedTagIds: Set<String>) =
        repository.updateTags(ids, addedTagIds, removedTagIds)
}

class BatchTrashDocumentsUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend operator fun invoke(ids: Set<String>) = repository.moveToTrash(ids)
}

class ManageFoldersUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend fun create(name: String) = repository.createFolder(name)
    suspend fun rename(id: String, name: String) = repository.renameFolder(id, name)
    suspend fun delete(id: String) = repository.deleteFolder(id)
}

class ManageTagsUseCase @Inject constructor(private val repository: DocumentRepository) {
    suspend fun create(name: String) = repository.createTag(name)
    suspend fun rename(id: String, name: String) = repository.renameTag(id, name)
    suspend fun delete(id: String) = repository.deleteTag(id)
}
