package com.kdev66.ecoscan.report.data

import com.kdev66.ecoscan.detection.BoundingBox
import com.kdev66.ecoscan.detection.WasteClass
import com.kdev66.ecoscan.report.DetectionSource
import com.kdev66.ecoscan.report.Position
import com.kdev66.ecoscan.report.Report
import com.kdev66.ecoscan.report.ReportContext
import com.kdev66.ecoscan.report.ReportStatus
import com.kdev66.ecoscan.report.ReviewedDetection

internal fun toReport(
    entity: ReportEntity,
    detections: List<DetectionEntity>,
    counts: List<CategoryCountEntity>,
): Report = Report(
    id = entity.id,
    createdAtMs = entity.createdAtMs,
    utcOffsetMinutes = entity.utcOffsetMinutes,
    position = if (entity.latitude != null && entity.longitude != null) {
        Position(entity.latitude, entity.longitude, entity.accuracyM)
    } else {
        null
    },
    context = ReportContext.fromId(entity.context),
    comment = entity.comment,
    photoFile = entity.photoFile,
    modelVersion = entity.modelVersion,
    status = ReportStatus.valueOf(entity.status),
    detections = detections.map { it.toDomain() },
    counts = counts.filter { it.count > 0 }
        .associate { WasteClass.fromIndex(it.classIndex) to it.count }
        .toSortedMap(compareBy { it.ordinal }),
)

internal fun DetectionEntity.toDomain() = ReviewedDetection(
    id = id,
    source = DetectionSource.valueOf(source),
    wasteClass = WasteClass.fromIndex(classIndex),
    modelClass = modelClassIndex?.let(WasteClass::fromIndex),
    confidence = confidence,
    box = BoundingBox(x, y, x + width, y + height),
    deleted = deleted,
)

internal fun ReviewedDetection.toEntity(reportId: String) = DetectionEntity(
    id = id,
    reportId = reportId,
    source = source.name,
    classIndex = wasteClass.ordinal,
    modelClassIndex = modelClass?.ordinal,
    confidence = confidence,
    x = box.left,
    y = box.top,
    width = box.width,
    height = box.height,
    deleted = deleted,
)

internal fun Report.toEntity() = ReportEntity(
    id = id,
    createdAtMs = createdAtMs,
    utcOffsetMinutes = utcOffsetMinutes,
    latitude = position?.latitude,
    longitude = position?.longitude,
    accuracyM = position?.accuracyM,
    context = context.id,
    comment = comment,
    photoFile = photoFile,
    modelVersion = modelVersion,
    status = status.name,
)

internal fun Report.countEntities() = counts.map { (c, n) -> CategoryCountEntity(id, c.ordinal, n) }
