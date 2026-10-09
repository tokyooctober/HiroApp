package sg.hirokids.shared.domain

/** What an audience text says about its readers. */
internal enum class AudienceBand {
    CHILD,

    /** Teens, adults, or an age above the children's band. */
    OLDER,

    /** Neither a marker nor a veto. */
    UNKNOWN,
}

/** Reads the free text NLB puts in MARC 521 (`audience`), IMDA (`audienceImda`), subject headings and usage levels. */
internal object AudienceText {
    // NLB's primary band ends at 12 and its adolescent band starts at 13. An "N+" book also reaches teens, so it counts only up to 10.
    private const val MAX_CHILD_AGE = 12
    private const val MAX_CHILD_AGE_OPEN_ENDED = 10

    // UK Key Stages 1 and 2 are ages 5 to 11; Key Stage 3 starts at 11 and runs into the teens.
    private const val MAX_CHILD_KEY_STAGE = 2

    private val olderWords = Regex("""\b(adults?|mature|teens?|teenagers?|adolescents?)\b""")

    // "key stage 2", "key stage 1-2", "key stage 2 to 4": the highest stage named decides
    private val keyStage = Regex("""key stage\s*(\d)(?:\s*(?:-|–|to|and|&)\s*(\d))?""")

    // \b keeps "1990-1995" from being read as the ages 90-19
    private val ageRange = Regex("""\b(\d{1,2})\s*(?:-|–|to)\s*(\d{1,2})\b""")
    private val ageAndUp = Regex("""\b(\d{1,2})\s*\+""")

    private val rating = Regex("""\b(?:nc[\s-]?16|m[\s-]?18|r[\s-]?21)\b""", RegexOption.IGNORE_CASE)

    /**
     * Only the children's forms of the "Juvenile" subdivision count. "Juvenile delinquency", "Juvenile justice" and similar
     * headings on adult books must not pass; a form missing here is hidden, which is the safe failure.
     */
    private val juvenileHeading =
        Regex(
            """\bjuvenile\s+(?:literature|fiction|nonfiction|non-fiction|poetry|drama|humou?r|films?|sound recordings?|""" +
                """software|television programs?|websites?|periodicals?)\b""",
            RegexOption.IGNORE_CASE,
        )

    private val whitespace = Regex("""\s+""")

    /**
     * Teen or adult wording, or an age above the children's band, makes the text OLDER (and wins over everything else in it);
     * a young age range or Key Stage makes it CHILD; anything else is UNKNOWN.
     */
    fun band(raw: String): AudienceBand {
        val text = raw.lowercase()
        val bands =
            buildList {
                if (olderWords.containsMatchIn(text)) add(AudienceBand.OLDER)
                for (stages in keyStage.findAll(text)) {
                    val highest =
                        stages.groupValues
                            .drop(1)
                            .filter { it.isNotEmpty() }
                            .maxOf { it.toInt() }
                    add(childIf(highest <= MAX_CHILD_KEY_STAGE))
                }
                for (range in ageRange.findAll(text)) add(childIf(range.groupValues[2].toInt() <= MAX_CHILD_AGE))
                for (openEnded in ageAndUp.findAll(text)) add(childIf(openEnded.groupValues[1].toInt() <= MAX_CHILD_AGE_OPEN_ENDED))
            }
        return when {
            AudienceBand.OLDER in bands -> AudienceBand.OLDER
            AudienceBand.CHILD in bands -> AudienceBand.CHILD
            else -> AudienceBand.UNKNOWN
        }
    }

    /** A rating NLB treats as not for children: M18, NC16 or R21. */
    fun isRated(text: String): Boolean = rating.containsMatchIn(text)

    fun isJuvenileSubject(subject: String): Boolean = juvenileHeading.containsMatchIn(subject)

    /** `" junior  pb "` becomes `"JUNIOR PB"`. */
    fun normaliseUsageLevel(usageLevel: String): String = usageLevel.trim().uppercase().replace(whitespace, " ")

    private fun childIf(young: Boolean) = if (young) AudienceBand.CHILD else AudienceBand.OLDER
}
