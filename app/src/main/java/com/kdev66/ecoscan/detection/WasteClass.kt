package com.kdev66.ecoscan.detection

import androidx.annotation.StringRes
import com.kdev66.ecoscan.R

/**
 * Les 7 classes du modèle, **dans l'ordre de ses indices de sortie** (names du modèle entraîné :
 * Cardboard, Fabrics, Glass, Metal, Other, Plastic, Wood). Ne pas réordonner.
 */
enum class WasteClass(@StringRes val labelRes: Int, val argb: Long) {
    CARDBOARD(R.string.class_cardboard, 0xFFB0793C),
    FABRICS(R.string.class_fabrics, 0xFFE91E63),
    GLASS(R.string.class_glass, 0xFF00BCD4),
    METAL(R.string.class_metal, 0xFF90A4AE),
    OTHER(R.string.class_other, 0xFFAB47BC),
    PLASTIC(R.string.class_plastic, 0xFFFFC107),
    WOOD(R.string.class_wood, 0xFF8D6E63);

    companion object {
        fun fromIndex(index: Int): WasteClass = entries[index]
    }
}
