package com.ivy.planner.data

import com.ivy.planner.domain.Entry
import com.ivy.planner.domain.EntryKind
import com.ivy.planner.domain.EntryState
import com.ivy.planner.domain.IsoWeek
import com.ivy.planner.domain.OccurrenceRecord
import com.ivy.planner.domain.RepeatCodec
import com.ivy.planner.domain.RepeatSchedule
import com.ivy.planner.domain.Series
import java.time.LocalDate
import java.time.LocalTime

internal fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
internal fun LocalTime.toMinutes(): Int = hour * 60 + minute
internal fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)

private fun <T : Enum<T>> safeEnum(values: Array<T>, name: String, default: T): T =
    values.firstOrNull { it.name == name } ?: default

fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    kind = safeEnum(EntryKind.values(), kind, EntryKind.NOTE),
    title = title,
    description = description,
    date = date?.toLocalDate(),
    time = timeMinutes?.toLocalTime(),
    week = if (weekYear != null && weekNum != null) IsoWeek(weekYear, weekNum) else null,
    state = safeEnum(EntryState.values(), state, EntryState.OPEN),
    importance = importance,
    migrationCount = migrationCount,
    completedAt = completedAt,
    durationMinutes = durationMinutes,
    month = monthKey,
    createdAt = createdAt,
)

fun Entry.toEntity(createdAt: Long, now: Long): EntryEntity = EntryEntity(
    id = id,
    kind = kind.name,
    title = title,
    description = description,
    date = date?.toEpochDay(),
    timeMinutes = time?.toMinutes(),
    weekYear = week?.year,
    weekNum = week?.week,
    state = state.name,
    importance = importance,
    migrationCount = migrationCount,
    createdAt = createdAt,
    updatedAt = now,
    completedAt = completedAt,
    durationMinutes = durationMinutes,
    monthKey = month,
)

fun SeriesEntity.toDomain(): Series? {
    val rule = RepeatCodec.decodeRule(rule) ?: return null
    return Series(
        id = id,
        kind = safeEnum(EntryKind.values(), kind, EntryKind.TASK),
        title = title,
        description = description,
        time = timeMinutes?.toLocalTime(),
        schedule = RepeatSchedule(rule, startDate.toLocalDate(), RepeatCodec.decodeEnd(endRule)),
        paused = paused,
        durationMinutes = durationMinutes,
        isRoutine = isRoutine,
    )
}

fun Series.toEntity(createdAt: Long, now: Long): SeriesEntity = SeriesEntity(
    id = id,
    kind = kind.name,
    title = title,
    description = description,
    timeMinutes = time?.toMinutes(),
    rule = RepeatCodec.encodeRule(schedule.rule),
    startDate = schedule.start.toEpochDay(),
    endRule = RepeatCodec.encodeEnd(schedule.end),
    paused = paused,
    isRoutine = isRoutine,
    createdAt = createdAt,
    updatedAt = now,
    durationMinutes = durationMinutes,
)

fun OccurrenceEntity.toDomain(): OccurrenceRecord = OccurrenceRecord(
    seriesId = seriesId,
    date = date.toLocalDate(),
    state = safeEnum(EntryState.values(), state, EntryState.OPEN),
    titleOverride = titleOverride,
    descriptionOverride = descriptionOverride,
    timeOverride = timeOverride?.toLocalTime(),
    completedAt = completedAt,
)
