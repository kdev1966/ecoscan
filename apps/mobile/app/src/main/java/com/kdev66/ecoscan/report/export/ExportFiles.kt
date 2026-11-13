package com.kdev66.ecoscan.report.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kdev66.ecoscan.report.data.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Prépare l'archive d'export dans le cache de l'application et l'intention de partage Android.
 * Un seul export est gardé : le précédent est supprimé avant d'écrire le nouveau.
 */
class ExportFiles(private val context: Context, private val repository: ReportRepository) {

    data class Export(val file: File, val reports: Int, val missingPhotos: Int)

    /** Rend `null` s'il n'y a aucun signalement enregistré. */
    suspend fun exportAll(): Export? = withContext(Dispatchers.IO) {
        val reports = repository.savedReports()
        if (reports.isEmpty()) return@withContext null
        val dir = File(context.cacheDir, EXPORT_DIR).apply { deleteRecursively(); mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val file = File(dir, "ecoscan-$stamp.zip")
        val summary = file.outputStream().buffered().use { out ->
            ReportExport.writeZip(reports, repository::photoFile, out)
        }
        Export(file, summary.reports, summary.missingPhotos)
    }

    fun shareIntent(export: Export, chooserTitle: String): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", export.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, export.file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
