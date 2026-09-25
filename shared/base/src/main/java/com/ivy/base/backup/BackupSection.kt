package com.ivy.base.backup

import kotlinx.serialization.json.JsonElement

/**
 * A plug-in section of the app's backup file.
 *
 * The backup (a zip with one JSON file) stores every registered section under
 * `sections.<key>`. Features like the Apeiro planner register a section with Hilt
 * (`@Binds @IntoSet`) so the core backup code doesn't need to know about them.
 * Backups made before a section existed simply don't contain its key.
 */
interface BackupSection {
    /** Stable key in the backup file, e.g. "apeiroPlanner". Never change it. */
    val key: String

    suspend fun export(): JsonElement

    /** Merges the backed-up data into the database (existing rows with the same id are replaced). */
    suspend fun import(data: JsonElement)
}
