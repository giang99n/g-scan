package com.example.gscan.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signature_templates")
data class SignatureTemplateEntity(@PrimaryKey val id: String, val name: String, val ink: String)
