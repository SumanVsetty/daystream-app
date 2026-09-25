package com.ivy.planner.domain

/**
 * Sentence case: the first letter of the text (and of each line, for descriptions) is a capital.
 * The rest is left exactly as typed, so names like "L&T", "HDFC" or "iPhone" survive.
 */
object TextCase {
    fun sentence(text: String): String {
        val firstWord = text.takeWhile { !it.isWhitespace() }
        // leave words that already mix cases on purpose, like "iPhone" or "eBay"
        if (firstWord.drop(1).any { it.isUpperCase() }) return text
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun sentenceLines(text: String): String = text.lines().joinToString("\n") { line ->
        val start = line.indexOfFirst { !it.isWhitespace() }
        if (start < 0) line else line.substring(0, start) + sentence(line.substring(start))
    }
}
