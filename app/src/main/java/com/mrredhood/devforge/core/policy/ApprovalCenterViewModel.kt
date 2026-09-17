package com.mrredhood.devforge.core.policy

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrredhood.devforge.core.storage.ApprovalEntity
import com.mrredhood.devforge.core.storage.ApprovalRepository
import com.mrredhood.devforge.core.storage.DevForgeDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ApprovalCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ApprovalRepository(DevForgeDatabase.get(application).approvalDao())
    private var pendingJob: Job? = null

    var pending by mutableStateOf<List<ApprovalEntity>>(emptyList())
        private set

    var actionMessage by mutableStateOf<String?>(null)
        private set

    init {
        pendingJob = viewModelScope.launch {
            repository.observePending().collectLatest { actions ->
                pending = actions
            }
        }
        viewModelScope.launch { repository.expireDue() }
    }

    fun approve(action: ApprovalEntity) {
        viewModelScope.launch {
            val resolved = repository.approve(action.approvalId)
            actionMessage = if (resolved) "Approved: ${action.summary}" else "This approval is no longer pending."
        }
    }

    fun reject(action: ApprovalEntity) {
        viewModelScope.launch {
            val resolved = repository.reject(action.approvalId)
            actionMessage = if (resolved) "Rejected: ${action.summary}" else "This approval is no longer pending."
        }
    }

    fun clearMessage() {
        actionMessage = null
    }

    override fun onCleared() {
        pendingJob?.cancel()
        super.onCleared()
    }
}
