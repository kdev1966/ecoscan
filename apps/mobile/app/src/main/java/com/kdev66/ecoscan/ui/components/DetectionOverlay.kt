package com.kdev66.ecoscan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdev66.ecoscan.detection.DetectionResult
import com.kdev66.ecoscan.detection.WasteClass
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Comment l'image est placée dans la zone d'affichage, pour y reporter les boîtes. */
enum class ImageFit {
    /** L'image remplit la zone et déborde (aperçu caméra, PreviewView FILL_CENTER). */
    FILL,
    /** L'image tient entière dans la zone (photo du résultat, ContentScale.Fit). */
    FIT,
}

/** Dessine les boîtes, la classe et la confiance par-dessus une image affichée selon [fit]. */
@Composable
fun DetectionOverlay(result: DetectionResult, fit: ImageFit, modifier: Modifier = Modifier) {
    val labels = WasteClass.entries.associateWith { stringResource(it.labelRes) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)

    Canvas(modifier) {
        val imgW = result.imageWidth.toFloat()
        val imgH = result.imageHeight.toFloat()
        val scale = when (fit) {
            ImageFit.FILL -> max(size.width / imgW, size.height / imgH)
            ImageFit.FIT -> min(size.width / imgW, size.height / imgH)
        }
        val dx = (size.width - imgW * scale) / 2f
        val dy = (size.height - imgH * scale) / 2f
        val stroke = Stroke(width = 2.5.dp.toPx())

        for (d in result.detections) {
            val color = Color(d.wasteClass.argb)
            val topLeft = Offset(dx + d.box.left * imgW * scale, dy + d.box.top * imgH * scale)
            val boxSize = Size(d.box.width * imgW * scale, d.box.height * imgH * scale)
            drawRect(color, topLeft, boxSize, style = stroke)

            val text = "${labels.getValue(d.wasteClass)} ${(d.confidence * 100).roundToInt()}%"
            val layout = textMeasurer.measure(text, labelStyle)
            val labelTop = (topLeft.y - layout.size.height).coerceAtLeast(0f)
            drawRect(
                color,
                Offset(topLeft.x, labelTop),
                Size(layout.size.width + 8.dp.toPx(), layout.size.height.toFloat()),
            )
            drawText(layout, topLeft = Offset(topLeft.x + 4.dp.toPx(), labelTop))
        }
    }
}
