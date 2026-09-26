package com.ivy.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.planner.domain.Edit
import com.ivy.planner.domain.Format
import com.ivy.planner.domain.Formatter
import com.ivy.planner.domain.Markdown

/** Formatted text for reading: headings, lists, checkboxes (tickable), quotes, links. */
@Composable
fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 17,
    onToggleCheck: ((line: Int) -> Unit)? = null,
) {
    val uri = LocalUriHandler.current
    val text = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Markdown.parse(markdown).forEach { b ->
            val size = when {
                b.type == Markdown.BlockType.HEADING && b.level == 1 -> fontSize + 7
                b.type == Markdown.BlockType.HEADING -> fontSize + 3
                else -> fontSize
            }
            val annotated = spans(b.spans, strikeAll = b.type == Markdown.BlockType.CHECK && b.checked)
            val body: @Composable (Modifier) -> Unit = { m ->
                ClickableText(
                    text = annotated,
                    modifier = m,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = size.sp,
                        lineHeight = (size * 1.5).sp,
                        fontWeight = if (b.type == Markdown.BlockType.HEADING) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            b.type == Markdown.BlockType.QUOTE -> muted
                            b.type == Markdown.BlockType.CHECK && b.checked -> muted
                            else -> text
                        },
                        fontStyle = if (b.type == Markdown.BlockType.QUOTE) FontStyle.Italic else FontStyle.Normal,
                    ),
                    onClick = { offset ->
                        annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.let { runCatching { uri.openUri(it.item) } }
                    },
                )
            }
            when (b.type) {
                Markdown.BlockType.BULLET, Markdown.BlockType.NUMBERED -> Row {
                    Text(
                        if (b.type == Markdown.BlockType.BULLET) "•" else "${b.level}.",
                        Modifier.width(24.dp),
                        fontSize = size.sp,
                        color = muted,
                    )
                    body(Modifier.weight(1f))
                }
                Markdown.BlockType.CHECK -> Row(verticalAlignment = Alignment.Top) {
                    CheckCircle(
                        checked = b.checked,
                        onToggle = { onToggleCheck?.invoke(b.line) },
                        enabled = onToggleCheck != null,
                        size = 18.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    body(Modifier.weight(1f).padding(top = 2.dp))
                }
                Markdown.BlockType.QUOTE -> Row {
                    Box(Modifier.width(3.dp).height((size * 1.5).dp).background(PlannerColors.Journal))
                    Spacer(Modifier.width(10.dp))
                    body(Modifier.weight(1f))
                }
                else -> body(Modifier)
            }
        }
    }
}

private fun spans(list: List<Markdown.Span>, strikeAll: Boolean): AnnotatedString = buildAnnotatedString {
    list.forEach { s ->
        val style = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = when {
                s.link != null -> TextDecoration.Underline
                s.strike || strikeAll -> TextDecoration.LineThrough
                else -> null
            },
            color = if (s.link != null) PlannerColors.Event else Color.Unspecified,
        )
        // a local copy: properties from another module can't be smart-cast
        val link = s.link
        if (link != null) pushStringAnnotation("link", link)
        withStyle(style) { append(s.text) }
        if (link != null) pop()
    }
}

/**
 * Styles Markdown while typing: bold looks bold, headings larger, and the symbols faint.
 * The text itself is unchanged, so the cursor maps one to one.
 */
class MarkdownStyling(private val faint: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = buildAnnotatedString {
            append(raw)
            fun mark(r: IntRange) = addStyle(SpanStyle(color = faint), r.first, r.last + 1)
            Regex("""\*\*(.+?)\*\*""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
                mark(m.range.first..m.range.first + 1); mark(m.range.last - 1..m.range.last)
            }
            Regex("""~~(.+?)~~""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), m.range.first, m.range.last + 1)
                mark(m.range.first..m.range.first + 1); mark(m.range.last - 1..m.range.last)
            }
            Regex("""(?<![\w*])\*(?![\s*])(.+?)(?<![\s*])\*(?![\w*])""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), m.range.first, m.range.last + 1)
                mark(m.range.first..m.range.first); mark(m.range.last..m.range.last)
            }
            Regex("""(?m)^(#{1,3})\s+.*$""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp), m.range.first, m.range.last + 1)
                mark(m.range.first..m.range.first + m.groupValues[1].length - 1)
            }
            Regex("""(?m)^\s*([-*•]\s+\[[ xX]]|[-*•]|\d+[.)]|>)\s""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = PlannerColors.Accent, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
            }
            Regex("""\[([^]]+)]\(([^)\s]*)\)""").findAll(raw).forEach { m ->
                addStyle(SpanStyle(color = PlannerColors.Event), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/**
 * A description field with a formatting toolbar (shown while editing): bold, italic,
 * strikethrough, heading, lists, checklist, quote and link. Lists continue on Enter.
 */
@Composable
fun RichTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Description (optional)",
    minLines: Int = 2,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    var focused by remember { mutableStateOf(false) }
    val faint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    fun set(v: TextFieldValue) {
        field = v
        onValueChange(v.text)
    }
    Column(modifier) {
        OutlinedTextField(
            value = field,
            onValueChange = { v ->
                val continued = Formatter.continueList(field.text, v.text)
                if (continued != null && v.selection.collapsed) {
                    val cursor = v.selection.start + (continued.length - v.text.length)
                    set(TextFieldValue(continued, TextRange(cursor.coerceIn(0, continued.length))))
                } else {
                    set(v)
                }
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            label = { Text(placeholder) },
            minLines = minLines,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = MarkdownStyling(faint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (focused) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    Format.BOLD to "B", Format.ITALIC to "I", Format.STRIKE to "S", Format.HEADING to "H",
                    Format.BULLET to "•", Format.NUMBERED to "1.", Format.CHECK to "☐", Format.QUOTE to "❝", Format.LINK to "Link",
                ).forEach { (format, label) ->
                    Box(
                        Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                val e = Formatter.apply(Edit(field.text, field.selection.min, field.selection.max), format)
                                set(TextFieldValue(e.text, TextRange(e.start, e.end)))
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = if (format == Format.BOLD) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontStyle = if (format == Format.ITALIC) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = if (format == Format.STRIKE) TextDecoration.LineThrough else null,
                        )
                    }
                }
            }
        }
    }
}
