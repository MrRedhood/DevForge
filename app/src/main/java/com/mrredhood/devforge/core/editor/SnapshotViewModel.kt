package com.mrredhood.devforge.core.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel

class SnapshotViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SnapshotStore(application)

    var snapshots by mutableStateOf<List<ContentSnapshot>>(emptyList())
        private set

    var diff by mutableStateOf<List<DiffLine>>(emptyList())
        private set

    private val diffEngine = DiffEngine()

    fun load(uri: Uri) {
        snapshots = store.list(uri)
        diff = emptyList()
    }

    fun saveSnapshot(uri: Uri, name: String, content: String, reason: SnapshotReason = SnapshotReason.MANUAL) {
        store.save(store.create(uri, name, content, reason))
        load(uri)
    }

    fun compare(before: String, after: String) {
        diff = diffEngine.compare(before, after)
    }

    fun clearDiff() {
        diff = emptyList()
    }
}
