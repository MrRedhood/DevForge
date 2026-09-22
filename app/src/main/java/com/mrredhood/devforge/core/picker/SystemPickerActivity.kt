package com.mrredhood.devforge.core.picker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class SystemPickerActivity : ComponentActivity() {
    private val pickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ATTACHMENTS
        val output = Intent().putExtra(EXTRA_KIND, kind)
        result.data?.let { data ->
            data.data?.let { output.data = it }
            data.clipData?.let { output.clipData = it }
            output.addFlags(data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        }
        setResult(result.resultCode, output)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ATTACHMENTS
        val picker = if (kind == KIND_WORKSPACE) {
            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeTypeFor(kind)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }
        runCatching { pickerLauncher.launch(picker) }
            .onFailure { setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_KIND, kind)); finish() }
    }

    private fun mimeTypeFor(kind: String): String = when (kind) {
        KIND_PHOTO -> "image/*"
        KIND_VIDEO -> "video/*"
        KIND_AUDIO -> "audio/*"
        else -> "*/*"
    }

    companion object {
        const val EXTRA_KIND = "picker_kind"
        const val KIND_WORKSPACE = "workspace"
        const val KIND_ATTACHMENTS = "attachments"
        const val KIND_PHOTO = "photo"
        const val KIND_VIDEO = "video"
        const val KIND_AUDIO = "audio"
        const val KIND_DOCUMENT = "document"
        const val PICKER_REQUEST_CODE = 12071
    }
}
