package com.ivy.planner.domain

/**
 * A small Markdown reader for notes and journal entries: headings, bullet, numbered and
 * check lists, quotes and paragraphs, with bold, italic, strikethrough and links inside.
 */
object Markdown {
    enum class BlockType { PARAGRAPH, HEADING, BULLET, NUMBERED, CHECK, QUOTE }

    data class Span(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val strike: Boolean = false,
        val link: String? = null,
    )

    data class Block(
        val type: BlockType,
        val spans: List<Span>,
        /** Heading level (1–3), or the number of a numbered item. */
        val level: Int = 0,
        val checked: Boolean = false,
        /** The source line, so a checkbox can be ticked in place. */
        val line: Int = 0,
    )

    private val heading = Regex("""^(#{1,3})\s+(.*)$""")
    private val check = Regex("""^\s*[-*]\s+\[([ xX])]\s+(.*)$""")
    private val bullet = Regex("""^\s*[-*•]\s+(.*)$""")
    private val numbered = Regex("""^\s*(\d+)[.)]\s+(.*)$""")
    private val quote = Regex("""^\s*>\s?(.*)$""")

    fun parse(text: String): List<Block> {
        val out = mutableListOf<Block>()
        val paragraph = StringBuilder()
        var paragraphLine = 0
        fun flush() {
            if (paragraph.isNotEmpty()) {
                out += Block(BlockType.PARAGRAPH, inline(paragraph.toString()), line = paragraphLine)
                paragraph.clear()
            }
        }
        text.lines().forEachIndexed { i, raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                heading.matches(line) -> heading.find(line)!!.let { m ->
                    flush(); out += Block(BlockType.HEADING, inline(m.groupValues[2]), level = m.groupValues[1].length, line = i)
                }
                check.matches(line) -> check.find(line)!!.let { m ->
                    flush(); out += Block(BlockType.CHECK, inline(m.groupValues[2]), checked = m.groupValues[1] != " ", line = i)
                }
                bullet.matches(line) -> bullet.find(line)!!.let { m ->
                    flush(); out += Block(BlockType.BULLET, inline(m.groupValues[1]), line = i)
                }
                numbered.matches(line) -> numbered.find(line)!!.let { m ->
                    flush(); out += Block(BlockType.NUMBERED, inline(m.groupValues[2]), level = m.groupValues[1].toInt(), line = i)
                }
                quote.matches(line) -> quote.find(line)!!.let { m ->
                    flush(); out += Block(BlockType.QUOTE, inline(m.groupValues[1]), line = i)
                }
                else -> {
                    if (paragraph.isEmpty()) paragraphLine = i else paragraph.append('\n')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return out
    }

    private val inlineToken = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)|\[([^]]+)]\(([^)\s]+)\)|(https?://[^\s)]+)""")

    /** Inline formatting: **bold**, *italic* or _italic_, ~~strike~~, [text](url) and bare links. */
    fun inline(text: String, bold: Boolean = false, italic: Boolean = false, strike: Boolean = false): List<Span> {
        val out = mutableListOf<Span>()
        var pos = 0
        inlineToken.findAll(text).forEach { m ->
            if (m.range.first > pos) out += Span(text.substring(pos, m.range.first), bold, italic, strike)
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> out += inline(g[1], true, italic, strike)
                g[2].isNotEmpty() -> out += inline(g[2], bold, italic, true)
                g[3].isNotEmpty() -> out += inline(g[3], bold, true, strike)
                g[4].isNotEmpty() -> out += inline(g[4], bold, true, strike)
                g[5].isNotEmpty() -> out += Span(g[5], bold, italic, strike, link = g[6])
                g[7].isNotEmpty() -> out += Span(g[7], bold, italic, strike, link = g[7])
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) out += Span(text.substring(pos), bold, italic, strike)
        return out
    }

    /** Plain text for previews on timelines: the symbols removed, one line per block. */
    fun plain(text: String): String = parse(text).joinToString("\n") { b ->
        val prefix = when (b.type) {
            BlockType.CHECK -> if (b.checked) "☑ " else "☐ "
            BlockType.BULLET -> "• "
            BlockType.NUMBERED -> "${b.level}. "
            else -> ""
        }
        prefix + b.spans.joinToString("") { it.text }
    }

    /** Ticks or unticks the checkbox on source [line]. */
    fun toggleCheck(text: String, line: Int): String {
        val lines = text.lines().toMutableList()
        val l = lines.getOrNull(line) ?: return text
        lines[line] = when {
            Regex("""\[[xX]]""").containsMatchIn(l) -> l.replaceFirst(Regex("""\[[xX]]"""), "[ ]")
            l.contains("[ ]") -> l.replaceFirst("[ ]", "[x]")
            else -> l
        }
        return lines.joinToString("\n")
    }
}

/** Formatting applied from the editor's toolbar. */
enum class Format { BOLD, ITALIC, STRIKE, HEADING, BULLET, NUMBERED, CHECK, QUOTE, LINK }

/** Text plus a selection (start..end, end exclusive), as the editor sees it. */
data class Edit(val text: String, val start: Int, val end: Int)

object Formatter {
    /**
     * Applies [format] at the selection. Inline formats wrap the selection (or insert a pair
     * of markers at the cursor) and unwrap when already applied; line formats toggle a prefix
     * on every selected line.
     */
    fun apply(edit: Edit, format: Format): Edit = when (format) {
        Format.BOLD -> wrap(edit, "**")
        Format.ITALIC -> wrap(edit, "*")
        Format.STRIKE -> wrap(edit, "~~")
        Format.LINK -> link(edit)
        Format.HEADING -> prefix(edit, "## ")
        Format.BULLET -> prefix(edit, "- ")
        Format.NUMBERED -> numbered(edit)
        Format.CHECK -> prefix(edit, "- [ ] ")
        Format.QUOTE -> prefix(edit, "> ")
    }

    private fun wrap(e: Edit, m: String): Edit {
        val t = e.text
        val (s, en) = e.start.coerceIn(0, t.length) to e.end.coerceIn(0, t.length)
        // already wrapped: remove the markers
        if (s >= m.length && en + m.length <= t.length && t.substring(s - m.length, s) == m && t.substring(en, en + m.length) == m) {
            val out = t.substring(0, s - m.length) + t.substring(s, en) + t.substring(en + m.length)
            return Edit(out, s - m.length, en - m.length)
        }
        val out = t.substring(0, s) + m + t.substring(s, en) + m + t.substring(en)
        return Edit(out, s + m.length, en + m.length)
    }

    private fun link(e: Edit): Edit {
        val t = e.text
        val label = t.substring(e.start, e.end).ifEmpty { "link" }
        val insert = "[$label](https://)"
        val out = t.substring(0, e.start) + insert + t.substring(e.end)
        // cursor inside the brackets, ready to type the address
        val cursor = e.start + insert.length - 1
        return Edit(out, cursor, cursor)
    }

    /** The first and last line index covered by the selection. */
    private fun lineRange(t: String, s: Int, en: Int): Pair<Int, Int> {
        val first = t.substring(0, s.coerceIn(0, t.length)).count { it == '\n' }
        val last = t.substring(0, en.coerceIn(0, t.length)).count { it == '\n' }
        return first to last
    }

    private val anyPrefix = Regex("""^(#{1,3}\s+|[-*]\s+\[[ xX]]\s+|[-*•]\s+|\d+[.)]\s+|>\s?)""")

    private fun prefix(e: Edit, p: String): Edit {
        val lines = e.text.lines().toMutableList()
        val (a, b) = lineRange(e.text, e.start, e.end)
        val all = (a..b).all { lines[it].startsWith(p) }
        var delta = 0
        var firstDelta = 0
        for (i in a..b) {
            val before = lines[i]
            lines[i] = if (all) before.removePrefix(p) else p + before.replaceFirst(anyPrefix, "")
            val d = lines[i].length - before.length
            if (i == a) firstDelta = d
            delta += d
        }
        return Edit(lines.joinToString("\n"), (e.start + firstDelta).coerceAtLeast(0), (e.end + delta).coerceAtLeast(0))
    }

    private fun numbered(e: Edit): Edit {
        val lines = e.text.lines().toMutableList()
        val (a, b) = lineRange(e.text, e.start, e.end)
        val all = (a..b).all { Regex("""^\d+[.)]\s+""").containsMatchIn(lines[it]) }
        var delta = 0
        var firstDelta = 0
        for ((n, i) in (a..b).withIndex()) {
            val before = lines[i]
            lines[i] = if (all) before.replaceFirst(Regex("""^\d+[.)]\s+"""), "") else "${n + 1}. " + before.replaceFirst(anyPrefix, "")
            val d = lines[i].length - before.length
            if (i == a) firstDelta = d
            delta += d
        }
        return Edit(lines.joinToString("\n"), (e.start + firstDelta).coerceAtLeast(0), (e.end + delta).coerceAtLeast(0))
    }

    /**
     * Pressing Enter at the end of a list item continues the list ("- ", "2. ", "- [ ] ");
     * on an empty item it ends the list. Returns null when nothing special applies.
     */
    fun continueList(before: String, after: String): String? {
        // "after" is the text just after a newline was typed at the cursor
        if (after.length != before.length + 1) return null
        val i = (0 until after.length).firstOrNull { k -> k >= before.length || after[k] != before[k] } ?: return null
        if (after[i] != '\n') return null
        val lineStart = before.lastIndexOf('\n', i - 1) + 1
        val line = before.substring(lineStart, i)
        val marker = Regex("""^(\s*)([-*]\s+\[[ xX]]\s+|[-*•]\s+|(\d+)[.)]\s+)""").find(line) ?: return null
        val content = line.substring(marker.value.length)
        if (content.isBlank()) {
            // empty item: end the list
            return before.substring(0, lineStart) + before.substring(i)
        }
        val num = marker.groupValues[3]
        val next = marker.groupValues[1] + when {
            num.isNotEmpty() -> "${num.toInt() + 1}. "
            marker.groupValues[2].contains("[") -> "- [ ] "
            else -> marker.groupValues[2]
        }
        return after.substring(0, i + 1) + next + after.substring(i + 1)
    }
}
