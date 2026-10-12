package com.github.kr328.clash.service

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.RemoteException
import androidx.core.content.edit
import com.github.kr328.clash.common.constants.Authorities
import com.github.kr328.clash.common.store.StoreProvider
import com.github.kr328.clash.common.store.asStoreProvider

/**
 * The settings file `service` belongs to the `:background` process: `SharedPreferences` keeps its
 * own copy in memory in every process, so two processes on one file would overwrite each other.
 * Every other process reads and writes through this provider, which is not exported (D-263).
 */
class PreferenceProvider : ContentProvider() {
    private val preferences: SharedPreferences by lazy {
        context!!.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val key = requireNotNull(arg) { "No key for $method" }
        val reply = Bundle()

        when (method) {
            GET_INT -> if (preferences.contains(key)) reply.putInt(VALUE, preferences.getInt(key, 0))
            GET_LONG -> if (preferences.contains(key)) reply.putLong(VALUE, preferences.getLong(key, 0))
            GET_BOOLEAN -> if (preferences.contains(key)) reply.putBoolean(VALUE, preferences.getBoolean(key, false))
            GET_STRING -> preferences.getString(key, null)?.let { reply.putString(VALUE, it) }
            GET_STRING_SET -> preferences.getStringSet(key, null)?.let { reply.putStringArrayList(VALUE, ArrayList(it)) }
            // apply(), as the services' own writes to this file: some callers inside this process
            // are on its main thread.
            SET_INT -> preferences.edit { putInt(key, extras.withValue().getInt(VALUE)) }
            SET_LONG -> preferences.edit { putLong(key, extras.withValue().getLong(VALUE)) }
            SET_BOOLEAN -> preferences.edit { putBoolean(key, extras.withValue().getBoolean(VALUE)) }
            SET_STRING -> preferences.edit { putString(key, extras.withValue().getString(VALUE)) }
            SET_STRING_SET -> preferences.edit { putStringSet(key, extras.withValue().getStringArrayList(VALUE)!!.toSet()) }
            else -> throw IllegalArgumentException("Unknown method $method")
        }

        reply.putBoolean(REPLIED, true)

        return reply
    }

    /** A write without a value must fail rather than store what `Bundle` returns for a missing key. */
    private fun Bundle?.withValue(): Bundle =
        this?.takeIf { it.containsKey(VALUE) } ?: throw IllegalArgumentException("No value to write")

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = unsupported()

    override fun getType(uri: Uri): String? = unsupported()

    override fun insert(uri: Uri, values: ContentValues?): Uri? = unsupported()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Settings are reached through call()")

    companion object {
        private const val FILE_NAME = "service"

        /** The file itself for the services that own it, the provider for everyone else. */
        fun createStoreProvider(context: Context): StoreProvider {
            return when (context) {
                is BaseService, is TunService ->
                    context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).asStoreProvider()
                else ->
                    RemoteStore(context.contentResolver)
            }
        }
    }
}

/**
 * The side of every process that does not own the file: each read and write is one call into
 * [PreferenceProvider].
 *
 * A call that failed never turns into the default. The caller may write what it has read back:
 * the app list is read, shown and saved again, and a default returned in silence would erase it.
 */
private class RemoteStore(private val resolver: ContentResolver) : StoreProvider {
    override fun getInt(key: String, defaultValue: Int): Int =
        call(GET_INT, key).run { if (containsKey(VALUE)) getInt(VALUE) else defaultValue }

    override fun setInt(key: String, value: Int) {
        call(SET_INT, key, Bundle().apply { putInt(VALUE, value) })
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        call(GET_LONG, key).run { if (containsKey(VALUE)) getLong(VALUE) else defaultValue }

    override fun setLong(key: String, value: Long) {
        call(SET_LONG, key, Bundle().apply { putLong(VALUE, value) })
    }

    override fun getString(key: String, defaultValue: String): String =
        call(GET_STRING, key).getString(VALUE) ?: defaultValue

    override fun setString(key: String, value: String) {
        call(SET_STRING, key, Bundle().apply { putString(VALUE, value) })
    }

    override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> =
        call(GET_STRING_SET, key).getStringArrayList(VALUE)?.toSet() ?: defaultValue

    override fun setStringSet(key: String, value: Set<String>) {
        call(SET_STRING_SET, key, Bundle().apply { putStringArrayList(VALUE, ArrayList(value)) })
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        call(GET_BOOLEAN, key).run { if (containsKey(VALUE)) getBoolean(VALUE) else defaultValue }

    override fun setBoolean(key: String, value: Boolean) {
        call(SET_BOOLEAN, key, Bundle().apply { putBoolean(VALUE, value) })
    }

    private fun call(method: String, key: String, extras: Bundle? = null): Bundle {
        // The owner process may be gone: the system kills it when the VPN is off, and the core can
        // crash it. Android starts it again for the next caller, so a dead process is worth one
        // more attempt; a second failure goes to the caller.
        val reply = try {
            attempt(method, key, extras)
        } catch (e: RemoteException) {
            attempt(method, key, extras)
        }

        // An exception the provider threw may arrive as an empty reply, so only a marked reply counts.
        return reply?.takeIf { it.getBoolean(REPLIED) }
            ?: error("Settings provider gave no answer to $method $key")
    }

    private fun attempt(method: String, key: String, extras: Bundle?): Bundle? {
        // An unstable reference: with a stable one (`ContentResolver.call`) Android kills this
        // process too when the owner process dies during the call.
        val client = resolver.acquireUnstableContentProviderClient(Authorities.SETTINGS_PROVIDER)
            ?: error("Settings provider is unavailable")

        return client.use { it.call(method, key, extras) }
    }
}

private const val GET_INT = "getInt"
private const val GET_LONG = "getLong"
private const val GET_BOOLEAN = "getBoolean"
private const val GET_STRING = "getString"
private const val GET_STRING_SET = "getStringSet"
private const val SET_INT = "setInt"
private const val SET_LONG = "setLong"
private const val SET_BOOLEAN = "setBoolean"
private const val SET_STRING = "setString"
private const val SET_STRING_SET = "setStringSet"

private const val VALUE = "value"

/** Present in every reply the provider has produced itself. */
private const val REPLIED = "replied"
