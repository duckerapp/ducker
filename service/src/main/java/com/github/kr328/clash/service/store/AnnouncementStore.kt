package com.github.kr328.clash.service.store

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** One announcement of a provider (stage 15): its text and when ducker first got it. */
@Serializable
data class Announcement(
    /** The subscription's number. */
    val profile: String,
    val text: String,
    val at: Long,
    /**
     * It was on screen before the history kept it — the first update after this feature came, or the same text outlived
     * [AnnouncementStore.KEEP]: it is never marked new, and its date is unknown.
     */
    val known: Boolean = false,
)

/**
 * The providers' announcements of the last [KEEP] (stage 15). Each update overwrites `announce` in `provider.json`;
 * this keeps the texts that came, one file for all subscriptions, so the «Подписки» bell shows them together. Written
 * by `:background` alone, after an update is committed; read by the interface. A write replaces the file through a
 * rename, so a reader never sees half of it.
 */
object AnnouncementStore {
    val KEEP: Long = TimeUnit.DAYS.toMillis(10)

    private const val FILE_NAME = "announcements.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Announcement.serializer())

    fun read(context: Context, now: Long = System.currentTimeMillis()): List<Announcement> =
        load(context).filter { now - it.at < KEEP }

    /**
     * After an update of [profile] is committed: [text] is the announcement it brought, [previous] — the one shown before
     * it.
     */
    @Synchronized
    fun record(context: Context, profile: UUID, text: String, previous: String, now: Long = System.currentTimeMillis()) {
        val all = load(context)

        all.recorded(profile.toString(), text, previous, now)?.let { save(context, it) }
    }

    @Synchronized
    fun forget(context: Context, profile: UUID) {
        val all = load(context)
        val kept = all.filter { it.profile != profile.toString() }

        if (kept.size != all.size) save(context, kept)
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private fun load(context: Context): List<Announcement> =
        runCatching { json.decodeFromString(serializer, file(context).readText()) }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<Announcement>) {
        runCatching {
            val target = file(context)
            val temp = File(target.parentFile, "$FILE_NAME.tmp")

            temp.writeText(json.encodeToString(serializer, list))
            temp.renameTo(target)
        }
    }
}

/** At most this many per subscription: a provider that changes the text every hour does not grow the file without end. */
private const val PER_PROFILE = 30

/**
 * The list after an update of [profile] brought [text]; `null` — nothing to write. A text is added only when it differs
 * from the subscription's last one: the provider repeats it with every update. Entries older than [AnnouncementStore.KEEP]
 * go.
 */
internal fun List<Announcement>.recorded(profile: String, text: String, previous: String, now: Long): List<Announcement>? {
    val kept = filter { now - it.at < AnnouncementStore.KEEP }
    val own = kept.filter { it.profile == profile }
    val shown = text.trim()

    if (shown.isEmpty() || own.maxByOrNull { it.at }?.text == shown) return kept.takeIf { it.size != size }

    val entry = Announcement(profile, shown, now, known = own.isEmpty() && previous.trim() == shown)

    return kept.filter { it.profile != profile } + (own + entry).takeLast(PER_PROFILE)
}
