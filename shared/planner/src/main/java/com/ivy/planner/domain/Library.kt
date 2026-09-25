package com.ivy.planner.domain

/** A board (tasks with progress) or a topic (a life timeline such as Car, House, Suchet · School). */
data class Collection(
    val id: String,
    val name: String,
    val color: Long,
    val type: CollectionType,
    val coverFile: String? = null,
)

enum class CollectionType { BOARD, TOPIC }

data class Person(val id: String, val name: String, val photoFile: String? = null)

/** Pastel colours offered for boards and collections (ARGB). */
object CollectionColors {
    val all: List<Long> = listOf(
        0xFF8FD4A3, 0xFF9DBBEF, 0xFFF2A48C, 0xFFC3B1EC, 0xFFF2D38C, 0xFFEF9A9A, 0xFF8FD0D4, 0xFFD4B48F,
    )
    fun next(used: Int): Long = all[used % all.size]
}

/** Default Lifely-style labels for importance levels 1–4 (editable in settings later). */
object ImportanceLabels {
    val defaults = listOf(
        "Just a cool event",
        "Very unusual event",
        "Really important event",
        "It has changed my life",
    )
    fun label(level: Int, labels: List<String> = defaults): String? = labels.getOrNull(level - 1)
}

/** Matches a hashtag like "quotes" or "tl-quotes" to a collection name like "TL-Quotes-Pending". */
object TagMatch {
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun find(tag: String, collections: List<Collection>): Collection? {
        val t = norm(tag)
        if (t.isEmpty()) return null
        return collections.firstOrNull { norm(it.name) == t }
            ?: collections.firstOrNull { norm(it.name).startsWith(t) }
            ?: collections.firstOrNull { norm(it.name).contains(t) }
    }
}
