package com.kdev66.ecoscan.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

/**
 * Convertit une image CameraX en bitmap remis à l'endroit (rotation du capteur appliquée).
 * [maxSide] réduit l'image si son plus grand côté le dépasse.
 */
fun ImageProxy.toUprightBitmap(maxSide: Int? = null): Bitmap {
    val source = toBitmap()
    val rotation = imageInfo.rotationDegrees
    val scale = maxSide?.let { min(1f, it.toFloat() / max(source.width, source.height)) } ?: 1f
    if (rotation == 0 && scale == 1f) return source
    val matrix = Matrix().apply {
        postScale(scale, scale)
        postRotate(rotation.toFloat())
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
