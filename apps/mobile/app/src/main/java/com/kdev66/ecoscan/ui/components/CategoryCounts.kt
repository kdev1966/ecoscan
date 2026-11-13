package com.kdev66.ecoscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kdev66.ecoscan.detection.WasteClass

/** Pastilles « couleur · classe · nombre », une par classe présente. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryCounts(
    counts: Map<WasteClass, Int>,
    modifier: Modifier = Modifier,
    chipColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((wasteClass, count) in counts) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(chipColor, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Spacer(Modifier.size(10.dp).background(Color(wasteClass.argb), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${stringResource(wasteClass.labelRes)} $count",
                    color = textColor,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
