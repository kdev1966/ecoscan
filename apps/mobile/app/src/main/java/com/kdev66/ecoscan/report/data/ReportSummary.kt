package com.kdev66.ecoscan.report.data

import com.kdev66.ecoscan.report.ReportContext
import java.io.File

/** Une ligne de l'historique. */
data class ReportSummary(
    val id: String,
    val createdAtMs: Long,
    val utcOffsetMinutes: Int,
    val context: ReportContext,
    val total: Int,
    val located: Boolean,
    val photo: File,
)
