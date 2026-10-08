package app.hisab.data

import com.russhwolf.settings.Settings
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** Saved guest login. The password only unlocks this person's own groups. */
@Serializable
data class Credentials(val email: String, val password: String)

/** A write made while offline, sent when the server is reachable again. */
@Serializable
data class PendingWrite(
    val collection: String, // "expenses" or "payments"
    val groupId: String,
    val body: String, // JSON with a client-chosen "id", so retries can't duplicate
)

/** Key-value storage for session, offline cache and the outbox. */
class Store(private val settings: Settings) {
    var credentials: Credentials?
        get() = read("credentials", Credentials.serializer())
        set(value) = write("credentials", Credentials.serializer(), value)

    var token: String?
        get() = settings.getStringOrNull("token")
        set(value) = if (value == null) settings.remove("token") else settings.putString("token", value)

    var userId: String?
        get() = settings.getStringOrNull("userId")
        set(value) = if (value == null) settings.remove("userId") else settings.putString("userId", value)

    var groups: List<GroupSummary>?
        get() = read("cache.groups", ListSerializer(GroupSummary.serializer()))
        set(value) = write("cache.groups", ListSerializer(GroupSummary.serializer()), value)

    fun group(id: String): GroupData? = read("cache.group.$id", GroupData.serializer())
    fun saveGroup(data: GroupData) = write("cache.group.${data.group.id}", GroupData.serializer(), data)

    var outbox: List<PendingWrite>
        get() = read("outbox", ListSerializer(PendingWrite.serializer())) ?: emptyList()
        set(value) = write("outbox", ListSerializer(PendingWrite.serializer()), value)

    /** Invite code opened from a link before the app was ready to handle it. */
    var pendingInvite: String?
        get() = settings.getStringOrNull("pendingInvite")
        set(value) = if (value == null) settings.remove("pendingInvite") else settings.putString("pendingInvite", value)

    fun clearAll() = settings.clear()

    private fun <T> read(key: String, serializer: KSerializer<T>): T? =
        settings.getStringOrNull(key)?.let { runCatching { HisabJson.decodeFromString(serializer, it) }.getOrNull() }

    private fun <T> write(key: String, serializer: KSerializer<T>, value: T?) {
        if (value == null) settings.remove(key) else settings.putString(key, HisabJson.encodeToString(serializer, value))
    }
}
