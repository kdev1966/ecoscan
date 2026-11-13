package com.kdev66.ecoscan.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import com.kdev66.ecoscan.R
import com.kdev66.ecoscan.report.ReportContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Date et heure de la capture, à l'heure du lieu de capture, dans la langue de l'application. */
fun formatCaptureDate(createdAtMs: Long, utcOffsetMinutes: Int): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(createdAtMs).atOffset(ZoneOffset.ofTotalSeconds(utcOffsetMinutes * 60)))

fun ReportContext.labelRes(): Int = when (this) {
    ReportContext.BEACH -> R.string.context_beach
    ReportContext.ROCKY_COAST -> R.string.context_rocky_coast
    ReportContext.FOREST -> R.string.context_forest
    ReportContext.CITY -> R.string.context_city
}

/** Photo lue hors du thread principal ; [maxSide] la réduit (miniatures). */
@Composable
fun rememberPhoto(file: File, maxSide: Int? = null): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, file, maxSide) {
        value = withContext(Dispatchers.IO) { decode(file, maxSide) }
    }
    return bitmap
}

@Composable
fun PhotoThumbnail(file: File, maxSide: Int, modifier: Modifier = Modifier) {
    val bitmap = rememberPhoto(file, maxSide)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
        }
    }
}

private fun decode(file: File, maxSide: Int?): Bitmap? {
    if (!file.isFile) return null
    if (maxSide == null) return BitmapFactory.decodeFile(file.path)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}
