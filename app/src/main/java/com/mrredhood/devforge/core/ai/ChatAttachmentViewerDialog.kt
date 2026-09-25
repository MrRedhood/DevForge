package com.mrredhood.devforge.core.ai

import android.content.Intent
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChatAttachmentViewerDialog(
    attachment: ChatMessageAttachment,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var error by remember(attachment.uri) { mutableStateOf<String?>(null) }
    var text by remember(attachment.uri) { mutableStateOf<String?>(null) }
    var image by remember(attachment.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    val mime = remember(attachment) {
        attachment.mimeType.ifBlank {
            MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(attachment.name.substringAfterLast('.', "").lowercase())
                .orEmpty()
        }.lowercase()
    }
    val extension = attachment.name.substringAfterLast('.', "").lowercase()
    val isText = mime.startsWith("text/") ||
        mime == "application/json" ||
        mime == "application/xml" ||
        mime == "application/rtf" ||
        extension in setOf(
            "txt", "md", "csv", "tsv", "kt", "java", "js", "ts", "py", "xml", "json", "html", "css", "scss", "sass", "less",
        )

    LaunchedEffect(attachment.uri, mime, isText) {
        error = null
        text = null
        image = null
        runCatching {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(attachment.uri)?.use { input ->
                    when {
                        isText -> {
                            val bytes = input.readBytes().take(512 * 1024).toByteArray()
                            if (bytes.any { it == 0.toByte() }) {
                                throw IllegalArgumentException("This file is binary.")
                            }
                            text = bytes.toString(Charsets.UTF_8)
                        }
                        mime.startsWith("image/") -> {
                            image = BitmapFactory.decodeStream(input)
                                ?: throw IllegalArgumentException("Unable to decode this image.")
                        }
                    }
                } ?: throw IllegalArgumentException("The selected file is no longer available.")
            }
        }.onFailure {
            error = it.message ?: "Unable to open this attachment."
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(attachment.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(
                            attachment.mimeType + " · " + formatAttachmentSize(attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close attachment")
                    }
                }

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                } else if (isText && text != null) {
                    Text(
                        text = text!!,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (mime.startsWith("image/") && image != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = image!!.asImageBitmap(),
                            contentDescription = attachment.name,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                } else {
                    Text(
                        when {
                            mime.startsWith("video/") -> "Video attachment. Open it to play."
                            mime.startsWith("audio/") -> "Audio attachment. Open it to play."
                            else -> "This file is handed to an installed Android viewer."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(attachment.uri, mime.ifBlank { "*/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }.onFailure {
                                error = it.message ?: "No compatible Android viewer is installed."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                        Text(
                            when {
                                mime.startsWith("video/") -> "Play video"
                                mime.startsWith("audio/") -> "Play audio"
                                else -> "Open file"
                            },
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Close") }
            }
        }
    }
}

private fun formatAttachmentSize(bytes: Long): String =
    when {
        bytes >= 1024L * 1024L -> (bytes / (1024L * 1024L)).toString() + " MB"
        bytes >= 1024L -> (bytes / 1024L).toString() + " KB"
        else -> bytes.toString() + " B"
    }
