package com.ivy.planner.domain

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RapidLogParserTest {
    private val today = LocalDate.of(2026, 9, 24) // Thursday
    private fun p(s: String) = RapidLogParser.parse(s, today)

    @Test
    fun `the example from the design discussion`() {
        val r = p("call Mr.Sunil regd L&T project today at 7pm")
        r.title shouldBe "call Mr.Sunil regd L&T project"
        r.date shouldBe today
        r.time shouldBe LocalTime.of(19, 0)
        r.kind shouldBe EntryKind.TASK
    }

    @Test
    fun `plain text is just a task title, kept exactly as typed`() {
        val r = p("Centum quote")
        r.title shouldBe "Centum quote"
        r.understoodSomething shouldBe false
    }

    @Test
    fun `note and event prefixes`() {
        p("- Great call with L&T").kind shouldBe EntryKind.NOTE
        p("- Great call with L&T").title shouldBe "Great call with L&T"
        p("o Suchet's school day on 28 sep").let {
            it.kind shouldBe EntryKind.EVENT
            it.date shouldBe LocalDate.of(2026, 9, 28)
            it.title shouldBe "Suchet's school day"
        }
    }

    @Test
    fun `times`() {
        p("gym 7:30 pm").time shouldBe LocalTime.of(19, 30)
        p("standup 09:45").time shouldBe LocalTime.of(9, 45)
        p("call at 7").time shouldBe LocalTime.of(19, 0)
        p("call at 9").time shouldBe LocalTime.of(9, 0)
        p("review at 19").time shouldBe LocalTime.of(19, 0)
        p("lunch at noon").time shouldBe LocalTime.NOON
        p("pay rent 12am").time shouldBe LocalTime.MIDNIGHT
        p("water plants in the evening").time shouldBe LocalTime.of(18, 0)
    }

    @Test
    fun `days`() {
        p("send quote tomorrow").date shouldBe today.plusDays(1)
        p("send quote day after tomorrow").date shouldBe today.plusDays(2)
        p("dinner tonight").let { it.date shouldBe today; it.time shouldBe LocalTime.of(20, 0) }
        p("meet Raj on friday").date shouldBe LocalDate.of(2026, 9, 25)
        p("meet Raj next thu").date shouldBe LocalDate.of(2026, 10, 1)
        p("report this thursday").date shouldBe today
        p("bill 3 oct").date shouldBe LocalDate.of(2026, 10, 3)
        p("bill oct 3rd").date shouldBe LocalDate.of(2026, 10, 3)
        p("renew 1/1").date shouldBe LocalDate.of(2027, 1, 1)
    }

    @Test
    fun `durations`() {
        p("workout for 45 min").durationMinutes shouldBe 45
        p("deep work for 1.5 hours tomorrow 10am").let {
            it.durationMinutes shouldBe 90
            it.time shouldBe LocalTime.of(10, 0)
            it.date shouldBe today.plusDays(1)
            it.title shouldBe "deep work"
        }
    }

    @Test
    fun `ordinary words aren't mistaken for dates`() {
        p("sat with Sunil about invoices").date shouldBe null
        p("sunil follow up").date shouldBe null
        p("check 2 invoices").let { it.date shouldBe null; it.time shouldBe null; it.title shouldBe "check 2 invoices" }
    }

    @Test
    fun `hashtags pick the board and leave the title clean`() {
        val r = p("Hamayan A818 quote #quotes tomorrow")
        r.title shouldBe "Hamayan A818 quote"
        r.tags shouldBe listOf("quotes")
        r.date shouldBe today.plusDays(1)
        p("fix invoice #tl-quotes #urgent").tags shouldBe listOf("tl-quotes", "urgent")
        p("L&T #1 priority").tags shouldBe listOf("1")
    }

    @Test
    fun `sentence case keeps the rest as typed`() {
        TextCase.sentence("iPhone repair") shouldBe "iPhone repair"
        TextCase.sentence("call mr.sunil") shouldBe "Call mr.sunil"
        TextCase.sentence("call HDFC") shouldBe "Call HDFC"
        TextCase.sentenceLines("karthik lawyer\n  startup application") shouldBe "Karthik lawyer\n  Startup application"
    }

    @Test
    fun `tags match collections loosely`() {
        val cs = listOf(
            Collection("1", "TL-Quotes-Pending", 0, CollectionType.BOARD),
            Collection("2", "Car", 0, CollectionType.TOPIC),
        )
        TagMatch.find("quotes", cs)?.id shouldBe "1"
        TagMatch.find("tl-quotes", cs)?.id shouldBe "1"
        TagMatch.find("CAR", cs)?.id shouldBe "2"
        TagMatch.find("house", cs) shouldBe null
    }

    @Test
    fun `dates with a year, including the past`() {
        p("moved to Bengaluru on 29 oct 2010").let {
            it.date shouldBe LocalDate.of(2010, 10, 29)
            it.title shouldBe "moved to Bengaluru"
        }
        p("wedding oct 29 2010").date shouldBe LocalDate.of(2010, 10, 29)
        p("bought the car 25/10/2019").date shouldBe LocalDate.of(2019, 10, 25)
        p("renew licence 1/1/2030").date shouldBe LocalDate.of(2030, 1, 1)
    }

    @Test
    fun `an exclamation mark at the start makes it one of today's 3`() {
        p("! Close L&T proposal at 3pm").let {
            it.focus shouldBe true
            it.title shouldBe "Close L&T proposal"
            it.time shouldBe LocalTime.of(15, 0)
        }
        p("Close proposal!").focus shouldBe false
        p("- ! a note").focus shouldBe false
    }
}
