package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test

class MarkdownTest {
    @Test
    fun `blocks`() {
        val b = Markdown.parse("# Title\nFirst line\nsecond line\n\n- apples\n- [x] done\n- [ ] todo\n2. two\n> quoted")
        b.map { it.type } shouldBe listOf(
            Markdown.BlockType.HEADING, Markdown.BlockType.PARAGRAPH, Markdown.BlockType.BULLET,
            Markdown.BlockType.CHECK, Markdown.BlockType.CHECK, Markdown.BlockType.NUMBERED, Markdown.BlockType.QUOTE,
        )
        b[1].spans.joinToString("") { it.text } shouldBe "First line\nsecond line"
        b[3].checked shouldBe true
        b[4].line shouldBe 6
        b[5].level shouldBe 2
    }

    @Test
    fun `inline formatting`() {
        val s = Markdown.inline("a **bold** and *it* and ~~gone~~ [site](https://x.in) end")
        s.filter { it.bold }.map { it.text } shouldBe listOf("bold")
        s.filter { it.italic }.map { it.text } shouldBe listOf("it")
        s.filter { it.strike }.map { it.text } shouldBe listOf("gone")
        s.first { it.link != null }.let { it.text shouldBe "site"; it.link shouldBe "https://x.in" }
        Markdown.inline("snake_case_name stays and 2 * 3 * 4").all { !it.italic } shouldBe true
        Markdown.inline("see https://apeiro.app now").first { it.link != null }.text shouldBe "https://apeiro.app"
    }

    @Test
    fun `plain text for previews`() {
        Markdown.plain("## Plan\n- [ ] **call** Sunil\n- buy milk") shouldBe "Plan\n☐ call Sunil\n• buy milk"
    }

    @Test
    fun `tick a checkbox in place`() {
        val t = "Shopping\n- [ ] milk\n- [x] eggs"
        Markdown.toggleCheck(t, 1) shouldBe "Shopping\n- [x] milk\n- [x] eggs"
        Markdown.toggleCheck(t, 2) shouldBe "Shopping\n- [ ] milk\n- [ ] eggs"
    }

    @Test
    fun `toolbar wraps and unwraps`() {
        val e = Formatter.apply(Edit("call Sunil now", 5, 10), Format.BOLD)
        e shouldBe Edit("call **Sunil** now", 7, 12)
        Formatter.apply(e, Format.BOLD) shouldBe Edit("call Sunil now", 5, 10)
        Formatter.apply(Edit("ab", 1, 1), Format.ITALIC) shouldBe Edit("a**b", 2, 2)
    }

    @Test
    fun `toolbar line prefixes`() {
        Formatter.apply(Edit("milk\neggs", 0, 9), Format.CHECK).text shouldBe "- [ ] milk\n- [ ] eggs"
        Formatter.apply(Edit("- milk\n- eggs", 0, 12), Format.BULLET).text shouldBe "milk\neggs"
        Formatter.apply(Edit("a\nb\nc", 0, 5), Format.NUMBERED).text shouldBe "1. a\n2. b\n3. c"
        Formatter.apply(Edit("- milk", 2, 2), Format.HEADING).text shouldBe "## milk"
    }

    @Test
    fun `lists continue on enter and end on an empty item`() {
        Formatter.continueList("- milk", "- milk\n") shouldBe "- milk\n- "
        Formatter.continueList("2. two", "2. two\n") shouldBe "2. two\n3. "
        Formatter.continueList("- [x] done", "- [x] done\n") shouldBe "- [x] done\n- [ ] "
        Formatter.continueList("- milk\n- ", "- milk\n- \n") shouldBe "- milk\n"
        Formatter.continueList("plain", "plain\n") shouldBe null
    }

    @Test
    fun `link inserts a template`() {
        val e = Formatter.apply(Edit("see site", 4, 8), Format.LINK)
        e.text shouldBe "see [site](https://)"
        e.start shouldBe e.text.length - 1
    }
}
