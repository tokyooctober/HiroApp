package sg.hirokids.shared.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import sg.hirokids.shared.db.AppDatabase
import sg.hirokids.shared.domain.Title

/** Titles kept on the phone by BRN, with no expiry (FR-14). */
interface BookStore {
    fun put(titles: List<Title>)

    fun get(brn: Long): Title?
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
        database.bookQueries.selectByBrn(brn).executeAsOneOrNull()?.let {
            Title(
                brn = it.brn,
                isbn = it.isbn,
                title = it.title,
                author = it.author,
                coverUrl = it.cover_url,
                subjects = decode(it.subjects),
                audience = decode(it.audience),
                audienceImda = decode(it.audience_imda),
                isRestricted = it.is_restricted != 0L,
                reservations = it.reservations.toInt(),
                format = it.format,
            )
        }
}
