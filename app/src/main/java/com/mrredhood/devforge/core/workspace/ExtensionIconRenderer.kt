package com.mrredhood.devforge.core.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.mrredhood.devforge.core.extension.ExtensionIconThemeStore
import com.mrredhood.devforge.core.extension.ExtensionPackageStore
import java.io.File

@Composable
internal fun ExtensionThemeIcon(
    name: String,
    isFolder: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val store = androidx.compose.runtime.remember { ExtensionPackageStore(context) }
    val iconStore = androidx.compose.runtime.remember { ExtensionIconThemeStore(context) }
    val asset = androidx.compose.runtime.remember(name, isFolder, expanded, store.activeIconThemeId()) {
        iconStore.resolve(name, isFolder, expanded, store)?.path
    }
    if (asset.isNullOrBlank()) {
        fallback()
        return
    }
    val bitmap by produceState<Bitmap?>(initialValue = null, asset) {
        value = loadAsset(File(asset))
    }
    val image = bitmap
    if (image == null) fallback()
    else Image(
        bitmap = image.asImageBitmap(),
        contentDescription = name,
        modifier = modifier,
    )
}

private fun loadAsset(file: File): Bitmap? = runCatching {
    require(file.isFile)
    if (file.extension.equals("svg", ignoreCase = true)) {
        val svg = file.inputStream().use { SVG.getFromInputStream(it) }
        val picture = svg.renderToPicture(72, 72)
        return@runCatching Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).drawPicture(picture)
        }
    }
    BitmapFactory.decodeFile(file.absolutePath)
}.getOrNull()
