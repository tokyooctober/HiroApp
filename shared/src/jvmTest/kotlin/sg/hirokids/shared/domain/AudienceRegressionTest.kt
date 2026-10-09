package sg.hirokids.shared.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs every recorded NLB response through [AudiencePolicy] in Children mode (spec section 8, Task 6; grows into T9).
 * JVM only: it reads `fixtures/` from the repository, which Gradle runs from the `shared` folder.
 *
 * Two directions are checked, because "zero leaks" alone is satisfied by a policy that hides everything:
 * nothing NLB classifies for adults may be allowed (unless the record itself carries a children's marker), and nothing that
 * carries a marker may be hidden. The hide rate is written to `build/reports/audience/hide-rate.txt` and printed.
 */
class AudienceRegressionTest {
    private val children = AudiencePolicy(Mode.CHILDREN)
    private val fixtures = File("../fixtures")

    private data class Fixture(
        val name: String,
        val records: List<Title>,
    )

    private fun load(name: String): Fixture {
        val root = Json.parseToJsonElement(File(fixtures, name).readText()).jsonObject
        val titles = root["titles"]?.jsonArray ?: JsonArray(emptyList())
        // SearchTitles nests records under each title; GetTitles returns the record itself
        val records = titles.flatMap { it.jsonObject["records"]?.jsonArray ?: listOf(it) }
        return Fixture(name, records.map { toTitle(it.jsonObject) })
    }

    private fun loadAll(prefix: String) =
        fixtures
            .listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".json") }!!
            .map { it.name }
            .sorted()
            .map(::load)

    private fun strings(element: JsonElement?): List<String> =
        element
            ?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()

    private fun toTitle(record: JsonObject) =
        Title(
            brn = record.getValue("brn").jsonPrimitive.longOrNull!!,
            isbn = strings(record["isbns"]).firstOrNull(),
            title = "",
            author = null,
            coverUrl = null,
            subjects = strings(record["subjects"]),
            audience = strings(record["audience"]),
            audienceImda = strings(record["audienceImda"]),
            isRestricted = record["isRestricted"]?.jsonPrimitive?.booleanOrNull ?: true, // missing means unknown: hide
            reservations = record["activeReservationsCount"]?.jsonPrimitive?.intOrNull ?: 0,
        )

    private val juvenileFixtures get() = loadAll("search-titles-junior-")
    private val adultFixtures get() = loadAll("search-titles-adult-")

    private fun hasMarker(title: Title) = title.subjects.any { it.contains("Juvenile", ignoreCase = true) } || title.audience.isNotEmpty()

    @Test
    fun fixturesAreThere() {
        assertTrue(juvenileFixtures.size >= 3, "juvenile fixtures missing")
        assertTrue(adultFixtures.size >= 2, "adult fixtures missing")
        assertTrue(juvenileFixtures.sumOf { it.records.size } > 40)
    }

    @Test
    fun noAdultOnlyRecordIsAllowedInChildrenMode() {
        val juvenileBrns = juvenileFixtures.flatMap { it.records }.map { it.brn }.toSet()
        val adultOnly = adultFixtures.flatMap { it.records }.filter { it.brn !in juvenileBrns }.distinctBy { it.brn }
        assertTrue(adultOnly.size >= 40, "expected the adult sample to be mostly adult-only, got ${adultOnly.size}")

        val leaked = adultOnly.filter { children.allows(it) }
        // The only adult-search records that may pass are those with an explicit children's marker (e.g. a "young readers adaptation").
        assertEquals(emptyList(), leaked.filterNot(::hasMarker).map { it.brn }, "adult-only records allowed in Children mode")
        val markedInAdultSearch = adultOnly.filter(::hasMarker)
        assertEquals(markedInAdultSearch.map { it.brn }.toSet(), leaked.map { it.brn }.toSet())
    }

    @Test
    fun everyMarkedChildrensRecordIsAllowedAndEveryUnmarkedOneIsHidden() {
        val records = juvenileFixtures.flatMap { it.records }.distinctBy { it.brn }
        for (record in records) {
            assertEquals(hasMarker(record), children.allows(record), "BRN ${record.brn}: ${record.subjects.take(2)} ${record.audience}")
        }
    }

    @Test
    fun noJuvenileFixtureRecordIsRestrictedOrRated() {
        val records = juvenileFixtures.flatMap { it.records }
        assertTrue(records.none { it.isRestricted }, "a restricted record is in the juvenile sample: the sample no longer tests that rule")
    }

    @Test
    fun theRecordedCopiesOfAChildrensBookAreAllCountable() {
        val items =
            Json
                .parseToJsonElement(File(fixtures, "get-availability-info.json").readText())
                .jsonObject
                .getValue("items")
                .jsonArray
                .map { it.jsonObject }
        val copies = items.map(::toCopy)
        assertEquals(9, copies.size)
        assertEquals(copies, children.countableCopies(copies), "usage levels seen: ${copies.map { it.usageLevel }.toSet()}")
    }

    private fun toCopy(item: JsonObject): Copy {
        fun code(key: String) =
            item
                .getValue(key)
                .jsonObject
                .getValue("code")
                .jsonPrimitive.content
        return Copy(
            brn = item.getValue("brn").jsonPrimitive.longOrNull!!,
            branchCode = code("location"),
            usageLevel = code("usageLevel"),
            status = if (code("transactionStatus") == "S") CopyStatus.AVAILABLE else CopyStatus.ON_LOAN,
            callNumber = (item["callNumber"] as? JsonPrimitive)?.contentOrNull,
            minAgeLimit = item["minAgeLimit"]?.jsonPrimitive?.intOrNull,
            media = code("media"),
        )
    }

    @Test
    fun hideRateReport() {
        val lines = mutableListOf<String>()
        for (fixture in juvenileFixtures) {
            val rejected = fixture.records.filterNot(children::allows)
            lines += "${fixture.name}: ${rejected.size} of ${fixture.records.size} records hidden"
            rejected.forEach { lines += "    hidden BRN ${it.brn} reason=${children.hideReason(it)} subjects=${it.subjects.take(3)}" }
        }
        // the same books appear in several fixtures, so the overall rate counts each BRN once
        val unique = juvenileFixtures.flatMap { it.records }.distinctBy { it.brn }
        val hidden = unique.count { !children.allows(it) }
        val percent = 100.0 * hidden / unique.size
        lines += "Children fixtures overall: $hidden of ${unique.size} distinct records hidden (${"%.1f".format(percent)}%)"
        val report = lines.joinToString("\n")

        val out = File("build/reports/audience/hide-rate.txt")
        out.parentFile.mkdirs()
        out.writeText(report + "\n")
        println(report)

        // Not a target: a guard against over-hiding. Spec section 9 expects around 20% on the first sample (non-English comics).
        assertTrue(percent <= MAX_HIDE_PERCENT, "hide rate ${"%.1f".format(percent)}% is above $MAX_HIDE_PERCENT%")
    }

    private companion object {
        const val MAX_HIDE_PERCENT = 25.0
    }
}
