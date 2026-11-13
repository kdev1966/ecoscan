package com.kdev66.ecoscan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.DetectionResult
import com.kdev66.ecoscan.detection.WasteClass
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Comment l'image est placée dans la zone d'affichage, pour y reporter les boîtes. */
enum class ImageFit {
    /** L'image remplit la zone et déborde (aperçu caméra, PreviewView FILL_CENTER). */
    FILL,
    /** L'image tient entière dans la zone (photo du signalement, ContentScale.Fit). */
    FIT,
}

/** Une boîte à dessiner. [key] identifie la boîte quand elle est touchée. */
data class OverlayBox(
    val key: Long,
    val box: BoundingBox,
    val wasteClass: WasteClass,
    val confidence: Float?,
    val selected: Boolean = false,
)

/** Boîtes de l'analyse en direct (aperçu caméra). */
@Composable
fun DetectionOverlay(result: DetectionResult, fit: ImageFit, modifier: Modifier = Modifier) {
    BoxOverlay(
        boxes = result.detections.mapIndexed { i, d -> OverlayBox(i.toLong(), d.box, d.wasteClass, d.confidence) },
        imageWidth = result.imageWidth,
        imageHeight = result.imageHeight,
        fit = fit,
        modifier = modifier,
    )
}

/**
 * Dessine les boîtes, la classe et la confiance par-dessus une image affichée selon [fit].
 * Avec [onTap], toucher l'image désigne la plus petite boîte qui contient le point (ou `null`).
 */
@Composable
fun BoxOverlay(
    boxes: List<OverlayBox>,
    imageWidth: Int,
    imageHeight: Int,
    fit: ImageFit,
    modifier: Modifier = Modifier,
    onTap: ((Long?) -> Unit)? = null,
) {
    val labels = WasteClass.entries.associateWith { stringResource(it.labelRes) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val imgW = imageWidth.toFloat()
    val imgH = imageHeight.toFloat()

    fun placement(width: Float, height: Float): Triple<Float, Float, Float> {
        val scale = when (fit) {
            ImageFit.FILL -> max(width / imgW, height / imgH)
            ImageFit.FIT -> min(width / imgW, height / imgH)
        }
        return Triple(scale, (width - imgW * scale) / 2f, (height - imgH * scale) / 2f)
    }

    val tapModifier = if (onTap == null) Modifier else Modifier.pointerInput(boxes, imageWidth, imageHeight) {
        detectTapGestures { tap ->
            val (scale, dx, dy) = placement(size.width.toFloat(), size.height.toFloat())
            val x = (tap.x - dx) / (imgW * scale)
            val y = (tap.y - dy) / (imgH * scale)
            val hit = boxes
                .filter { x in it.box.left..it.box.right && y in it.box.top..it.box.bottom }
                .minByOrNull { it.box.area }
            onTap(hit?.key)
        }
    }

    Canvas(modifier.then(tapModifier)) {
        val (scale, dx, dy) = placement(size.width, size.height)
        val normal = Stroke(width = 2.5.dp.toPx())
        val thick = Stroke(width = 5.dp.toPx())

        // La boîte sélectionnée est dessinée en dernier, par-dessus les autres.
        for (b in boxes.sortedBy { it.selected }) {
            val color = Color(b.wasteClass.argb)
            val topLeft = Offset(dx + b.box.left * imgW * scale, dy + b.box.top * imgH * scale)
            val boxSize = Size(b.box.width * imgW * scale, b.box.height * imgH * scale)
            if (b.selected) drawRect(Color.White, topLeft, boxSize, style = Stroke(width = 8.dp.toPx()))
            drawRect(color, topLeft, boxSize, style = if (b.selected) thick else normal)

            val text = labels.getValue(b.wasteClass) +
                (b.confidence?.let { " ${(it * 100).roundToInt()}%" } ?: "")
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
