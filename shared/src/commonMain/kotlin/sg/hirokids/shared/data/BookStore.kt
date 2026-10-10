package sg.hirokids.shared.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import sg.hirokids.shared.db.AppDatabase
import sg.hirokids.shared.db.Book
import sg.hirokids.shared.domain.Title

/** Titles kept on the phone by BRN, with no expiry (FR-14). */
interface BookStore {
    fun put(titles: List<Title>)

    fun get(brn: Long): Title?

    /** Saved titles whose title, author or ISBN contain every word of [words] (any case), at most [limit]. */
    fun search(
        words: String,
        limit: Int,
    ): List<Title>
}

class SqlBookStore(
    private val database: AppDatabase,
) : BookStore {
    private val strings = ListSerializer(String.serializer())

    private fun encode(list: List<String>) = Json.encodeToString(strings, list)

    private fun decode(text: String) = Json.decodeFromString(strings, text)

    override fun put(titles: List<Title>) {
        database.transaction {
            titles.forEach {
                database.bookQueries.upsert(
                    brn = it.brn,
                    isbn = it.isbn,
                    title = it.title,
                    author = it.author,
                    cover_url = it.coverUrl,
                    format = it.format,
                    subjects = encode(it.subjects),
                    audience = encode(it.audience),
                    audience_imda = encode(it.audienceImda),
                    is_restricted = if (it.isRestricted) 1L else 0L,
                    reservations = it.reservations.toLong(),
                )
            }
        }
    }

    override fun get(brn: Long): Title? =
        database.bookQueries
            .selectByBrn(brn)
            .executeAsOneOrNull()
            ?.toTitle()

    override fun search(
        words: String,
        limit: Int,
    ): List<Title> {
        val terms = words.lowercase().split(' ').filter { it.isNotBlank() }
        val anchor = terms.maxByOrNull { it.length } ?: return emptyList()
        return database.bookQueries
            .selectMatching(anchor, MAX_CANDIDATES)
            .executeAsList()
            .map { it.toTitle() }
            .filter { title ->
                val text = "${title.title} ${title.author.orEmpty()} ${title.isbn.orEmpty()}".lowercase()
                terms.all { it in text }
            }.take(limit)
    }

    private fun Book.toTitle() =
        Title(
            brn = brn,
            isbn = isbn,
            title = title,
            author = author,
            coverUrl = cover_url,
            subjects = decode(subjects),
            audience = decode(audience),
            audienceImda = decode(audience_imda),
            isRestricted = is_restricted != 0L,
            reservations = reservations.toInt(),
            format = format,
        )

    private companion object {
        const val MAX_CANDIDATES = 200L
    }
}
