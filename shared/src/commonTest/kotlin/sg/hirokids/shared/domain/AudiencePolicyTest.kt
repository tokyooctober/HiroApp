package sg.hirokids.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Children column of spec section 4, one rule at a time. Anything ambiguous must come back hidden. */
class AudiencePolicyTest {
    private val children = AudiencePolicy(Mode.CHILDREN)

    private val juvenile = listOf("Dinosaurs Juvenile literature.")

    private fun title(
        subjects: List<String> = emptyList(),
        audience: List<String> = emptyList(),
        audienceImda: List<String> = emptyList(),
        isRestricted: Boolean = false,
    ) = Title(
        brn = 1,
        isbn = null,
        title = "Dinosaur",
        author = null,
        coverUrl = null,
        subjects = subjects,
        audience = audience,
        audienceImda = audienceImda,
        isRestricted = isRestricted,
        reservations = 0,
    )

    private fun copy(
        usageLevel: String = "JUNIOR PB",
        minAgeLimit: Int? = 0,
        media: String? = "BOOK",
        status: CopyStatus = CopyStatus.AVAILABLE,
    ) = Copy(
        brn = 1,
        branchCode = "TRL",
        usageLevel = usageLevel,
        status = status,
        callNumber = "567.9 DIN",
        minAgeLimit = minAgeLimit,
        media = media,
    )

    // --- restricted -------------------------------------------------------------------------------------------------------

    @Test
    fun aRestrictedRecordIsHiddenEvenWithAJuvenileSubject() {
        assertEquals(HideReason.RESTRICTED, children.hideReason(title(subjects = juvenile, isRestricted = true)))
    }

    // --- juvenile marker --------------------------------------------------------------------------------------------------

    @Test
    fun aJuvenileSubjectIsAChildrensMarker() {
        assertNull(children.hideReason(title(subjects = juvenile)))
        assertNull(children.hideReason(title(subjects = listOf("Dinosaurs Juvenile fiction."))))
        assertNull(children.hideReason(title(subjects = listOf("Juvenile Nonfiction.", "Science."))))
        assertNull(children.hideReason(title(subjects = listOf("JUVENILE FICTION"))), "case does not matter")
    }

    @Test
    fun theMarkerCanBeAnyOfSeveralSubjects() {
        assertNull(children.hideReason(title(subjects = listOf("Dinosaurs", "Fossils", "Dinosaurs Juvenile literature."))))
    }

    @Test
    fun noSubjectsAndNoAudienceMeansNoMarkerSoHidden() {
        assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(title()))
    }

    @Test
    fun aRecordWithoutAnyMarkerIsHidden() {
        val malayComic = title(subjects = listOf("Dinosaurs Comic books, strips, etc.", "Zombies Comic books, strips, etc."))
        assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(malayComic))
    }

    @Test
    fun juvenileDelinquencyIsNotAChildrensBook() {
        // "Juvenile" appears in adult subject headings about crime and the courts; only the children's forms count.
        for (subject in listOf("Juvenile delinquency", "Juvenile justice, Administration of", "Juvenile offenders Rehabilitation")) {
            assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(title(subjects = listOf(subject))), subject)
        }
    }

    // --- audience field ---------------------------------------------------------------------------------------------------

    @Test
    fun anAgeRangeUpToTwelveIsAChildrensMarkerWithoutAnySubject() {
        for (text in listOf("9-12.", "4-6 years.", "04-06.", "0 to 3", "5 – 8")) {
            assertNull(children.hideReason(title(audience = listOf(text))), text)
        }
    }

    @Test
    fun anOpenEndedAgeIsAChildrensMarkerOnlyForYoungReaders() {
        assertNull(children.hideReason(title(audience = listOf("7+."))))
        assertNull(children.hideReason(title(audience = listOf("10+"))))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(audience = listOf("11+"))))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(audience = listOf("13+."))))
    }

    @Test
    fun keyStagesOneAndTwoAreChildrenAndLaterOnesAreNot() {
        assertNull(children.hideReason(title(audience = listOf("Key Stage 2."))))
        assertNull(children.hideReason(title(audience = listOf("Key Stage 1-2"))))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(audience = listOf("Key Stage 3."))))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(audience = listOf("Key Stage 2-4"))))
    }

    @Test
    fun anOlderAudienceVetoesAJuvenileSubject() {
        for (text in listOf("13-17.", "16+", "Adult.", "Young adult.", "Teen", "Mature readers", "Adolescent", "Pre-adolescent")) {
            assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(subjects = juvenile, audience = listOf(text))), text)
        }
    }

    @Test
    fun oneOlderValueAmongSeveralHidesTheRecord() {
        val mixed = title(audience = listOf("4-6 years.", "Adult."))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(mixed))
    }

    @Test
    fun aYearRangeIsNotAnAgeRange() {
        // "1990-1995" must not be read as ages "99-19" or "90-19"
        assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(title(audience = listOf("1990-1995"))))
    }

    @Test
    fun audienceTextWeCannotReadIsNeitherAMarkerNorAVeto() {
        assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(title(audience = listOf("P-01."))))
        assertNull(children.hideReason(title(subjects = juvenile, audience = listOf("P-01."))))
    }

    @Test
    fun theImdaFieldIsReadTheSameWay() {
        assertNull(children.hideReason(title(audienceImda = listOf("4-6 years"))))
        assertEquals(HideReason.OLDER_AUDIENCE, children.hideReason(title(subjects = juvenile, audienceImda = listOf("Adult"))))
    }

    // --- rated media ------------------------------------------------------------------------------------------------------

    @Test
    fun ratedMediaIsHiddenWhateverElseItSays() {
        for (rating in listOf("M18", "NC16", "NC-16", "NC 16", "R21", "m18")) {
            val rated = title(subjects = juvenile, audienceImda = listOf(rating))
            assertEquals(HideReason.RATED_MEDIA, children.hideReason(rated), rating)
        }
    }

    @Test
    fun aRatingInTheAudienceFieldIsAlsoCaught() {
        assertEquals(HideReason.RATED_MEDIA, children.hideReason(title(subjects = juvenile, audience = listOf("Rated NC16"))))
    }

    @Test
    fun aRatingLikeTokenInsideAnotherWordIsNotARating() {
        assertNull(children.hideReason(title(subjects = juvenile, audienceImda = listOf("G"))))
        assertNull(children.hideReason(title(subjects = juvenile, audience = listOf("Form18x"))))
    }

    // --- copies: usage level ----------------------------------------------------------------------------------------------

    @Test
    fun childrensShelvesAreAllowed() {
        for (level in listOf("JUNIOR", "JUNIOR PB", "JUNIOR RSING", "JUNIOR SING", "RACL", "ELL 0-3", "ELL 4-6")) {
            assertNull(children.hideReason(copy(usageLevel = level)), level)
        }
    }

    @Test
    fun usageLevelsAreComparedWithoutCaseOrExtraSpaces() {
        assertNull(children.hideReason(copy(usageLevel = " junior  pb ")))
    }

    @Test
    fun otherShelvesAreHidden() {
        for (level in listOf("ADULT", "TEEN", "ATT", "REFERENCE", "JUNIOR PBX", "")) {
            assertEquals(HideReason.NOT_A_CHILDREN_SHELF, children.hideReason(copy(usageLevel = level)), "'$level'")
        }
    }

    // --- copies: age limit and rating -------------------------------------------------------------------------------------

    @Test
    fun aMinimumAgeLimitHidesTheCopy() {
        assertEquals(HideReason.MIN_AGE_LIMIT, children.hideReason(copy(minAgeLimit = 16)))
        assertEquals(HideReason.MIN_AGE_LIMIT, children.hideReason(copy(minAgeLimit = 1)))
    }

    @Test
    fun noAgeLimitOrZeroIsFine() {
        assertNull(children.hideReason(copy(minAgeLimit = null)))
        assertNull(children.hideReason(copy(minAgeLimit = 0)))
    }

    @Test
    fun aRatedMediaCopyIsHidden() {
        assertEquals(HideReason.RATED_MEDIA, children.hideReason(copy(media = "DVD NC16")))
        assertEquals(HideReason.RATED_MEDIA, children.hideReason(copy(media = "Blu-ray (M18)")))
        assertNull(children.hideReason(copy(media = null)))
    }

    @Test
    fun theShelfStatusDoesNotDecideWhetherACopyIsShown() {
        // on loan or missing is about availability, not about who may see the book
        assertNull(children.hideReason(copy(status = CopyStatus.ON_LOAN)))
        assertNull(children.hideReason(copy(status = CopyStatus.MISSING)))
    }

    // --- title with copies ------------------------------------------------------------------------------------------------

    @Test
    fun mixedJuniorAndAdultCopiesCountOnlyTheJuniorOnes() {
        val junior = copy(usageLevel = "JUNIOR")
        val adult = copy(usageLevel = "ADULT")
        val tooOld = copy(usageLevel = "JUNIOR PB", minAgeLimit = 16)
        assertEquals(listOf(junior), children.countableCopies(listOf(adult, junior, tooOld)))
    }

    @Test
    fun aTitleWithJuniorAndAdultCopiesIsShownWithTheJuniorOnesCounted() {
        val copies = listOf(copy(usageLevel = "ADULT"), copy(usageLevel = "JUNIOR PB"), copy(usageLevel = "ADULT"))
        assertNull(children.hideReason(title(subjects = juvenile), copies))
        assertEquals(1, children.countableCopies(copies).size)
    }

    @Test
    fun aTitleWithOnlyAdultCopiesIsHidden() {
        val copies = listOf(copy(usageLevel = "ADULT"), copy(usageLevel = "TEEN"))
        assertEquals(HideReason.NO_COUNTABLE_COPY, children.hideReason(title(subjects = juvenile), copies))
    }

    @Test
    fun aTitleWithNoCopiesListedIsHiddenWhenCopiesAreRequired() {
        assertEquals(HideReason.NO_COUNTABLE_COPY, children.hideReason(title(subjects = juvenile), emptyList()))
    }

    @Test
    fun theRecordRulesComeBeforeTheCopyRules() {
        val copies = listOf(copy(usageLevel = "JUNIOR"))
        assertEquals(HideReason.RESTRICTED, children.hideReason(title(subjects = juvenile, isRestricted = true), copies))
        assertEquals(HideReason.NO_CHILDREN_MARKER, children.hideReason(title(), copies))
    }

    // --- ambiguity and the other mode -------------------------------------------------------------------------------------

    @Test
    fun allowsIsTrueOnlyWhenNothingHidesIt() {
        assertEquals(true, children.allows(title(subjects = juvenile)))
        assertEquals(false, children.allows(title()))
        assertEquals(true, children.allows(copy()))
        assertEquals(false, children.allows(copy(usageLevel = "ADULT")))
    }

    @Test
    fun adultModeIsNotBuiltYetSoItShowsNothing() {
        // The Adult column arrives in Task 12; until then nothing may be shown in that mode.
        val adult = AudiencePolicy(Mode.ADULT)
        assertEquals(HideReason.MODE_NOT_AVAILABLE, adult.hideReason(title(subjects = juvenile)))
        assertEquals(HideReason.MODE_NOT_AVAILABLE, adult.hideReason(copy(usageLevel = "ADULT")))
        assertEquals(emptyList(), adult.countableCopies(listOf(copy(usageLevel = "ADULT"))))
    }
}
