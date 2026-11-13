package com.kdev66.ecoscan.report.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReportEntity::class, DetectionEntity::class, CategoryCountEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class EcoScanDatabase : RoomDatabase() {
    abstract fun reportDao(): ReportDao

    companion object {
        fun create(context: Context): EcoScanDatabase =
            Room.databaseBuilder(context, EcoScanDatabase::class.java, "ecoscan.db").build()
    }
}
