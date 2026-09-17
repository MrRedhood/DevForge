package com.mrredhood.devforge.core.storage

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mrredhood.devforge.core.workspace.Workspace
import java.net.URI

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val treeUri: String,
    val lastOpenedAt: Long,
    val isActive: Boolean,
)

fun WorkspaceEntity.toDomain(): Workspace = Workspace(
    id = id,
    name = name,
    treeUri = android.net.Uri.parse(treeUri),
    lastOpenedAt = lastOpenedAt,
)

fun Workspace.toEntity(isActive: Boolean): WorkspaceEntity = WorkspaceEntity(
    id = id,
    name = name,
    treeUri = treeUri.toString(),
    lastOpenedAt = lastOpenedAt,
    isActive = isActive,
)
