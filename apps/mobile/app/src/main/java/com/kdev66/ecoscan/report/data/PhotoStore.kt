package com.kdev66.ecoscan.report.data

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Photos des signalements, dans le stockage interne de l'application.
 *
 * La photo est réencodée en JPEG depuis le bitmap décodé : elle ne porte donc aucune métadonnée EXIF de l'appareil
 * (PRD § F3). Écriture dans un fichier temporaire puis renommage : un fichier `.jpg` présent est toujours complet.
 */
class PhotoStore(private val dir: File) {

    /** Écrit la photo et rend son nom de fichier. */
    fun write(reportId: String, photo: Bitmap): String {
        dir.mkdirs()
        val name = "$reportId.jpg"
        val tmp = File(dir, "$name.tmp")
        FileOutputStream(tmp).use { out ->
            check(photo.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) { "encodage JPEG impossible" }
            out.fd.sync()
        }
        check(tmp.renameTo(File(dir, name))) { "renommage de $tmp impossible" }
        return name
    }

    fun file(name: String): File = File(dir, name)

    fun delete(name: String) {
        File(dir, name).delete()
    }

    /**
     * Supprime les fichiers qu'aucun signalement ne référence (écriture interrompue avant l'enregistrement en base)
     * et qui datent de plus de [minAgeMs] : une capture en cours n'est jamais touchée.
     */
    fun deleteOrphans(referenced: Set<String>, nowMs: Long, minAgeMs: Long): Int {
        val orphans = dir.listFiles().orEmpty().filter {
            it.isFile && it.name !in referenced && nowMs - it.lastModified() > minAgeMs
        }
        return orphans.count { it.delete() }
    }

    private companion object {
        const val JPEG_QUALITY = 85
    }
}
