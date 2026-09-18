package com.mrredhood.devforge.core.picker

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PickerResult(
    val kind: String,
    val uris: List<Uri>,
    val cancelled: Boolean,
)

object PickerBridge {
    private val resultsFlow = MutableSharedFlow<PickerResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val results: SharedFlow<PickerResult> = resultsFlow.asSharedFlow()

    fun emit(result: PickerResult) {
        resultsFlow.tryEmit(result)
    }
}
