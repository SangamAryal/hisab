package app.hisab.data

import app.hisab.core.Balances
import app.hisab.core.Share
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/** Data plus whether it came from the offline cache. */
data class Loaded<T>(val data: T, val offline: Boolean = false)

/**
 * Everything the screens need. Talks to PocketBase, keeps a guest session,
 * caches the last good data for offline viewing and queues offline writes.
 */
class Repository(
    private val pb: PocketBase,
    private val store: Store,
    val inviteBaseUrl: String = "hisab://join/",
) {
    private val sessionLock = Mutex()
    private var sessionChecked = false

    val myUserId: String? get() = store.userId

    // --- session ------------------------------------------------------------

    /** Makes sure we're signed in, creating a silent guest account the first time. */
    suspend fun ensureSession() = sessionLock.withLock {
        if (sessionChecked && pb.token != null) return@withLock
        val token = store.token
        if (token != null) {
            pb.token = token
            try {
                // An invalid token wouldn't fail list calls, it would just show
                // nothing, so check it once per launch.
                val auth: AuthResponse = pb.post("/api/collections/users/auth-refresh")
                saveAuth(auth)
                sessionChecked = true
                return@withLock
            } catch (e: ApiException) {
                pb.token = null
                store.token = null
            } catch (e: OfflineException) {
                return@withLock // keep the old token and work from the cache
            }
        }
        val creds = store.credentials ?: createGuest()
        login(creds)
        sessionChecked = true
    }

    private suspend fun createGuest(): Credentials {
        val creds = Credentials(
            email = "g_${randomId(20)}@guest.hisab.app",
            password = randomId(32),
        )
        pb.post<UserDto>(
            "/api/collections/users/records",
            buildJsonObject {
                put("email", creds.email)
                put("password", creds.password)
                put("passwordConfirm", creds.password)
            },
            auth = false,
        )
        store.credentials = creds
        return creds
    }

    private suspend fun login(creds: Credentials) {
        val auth: AuthResponse = pb.post(
            "/api/collections/users/auth-with-password",
            buildJsonObject {
                put("identity", creds.email)
                put("password", creds.password)
            },
            auth = false,
        )
        saveAuth(auth)
    }

    private fun saveAuth(auth: AuthResponse) {
        pb.token = auth.token
        store.token = auth.token
        store.userId = auth.record.id
    }

    /** A code that signs another phone into this same account. */
    @OptIn(ExperimentalEncodingApi::class)
    fun exportAccountCode(): String? {
        val c = store.credentials ?: return null
        return "HISAB1-" + Base64.UrlSafe.encode((c.email + "\n" + c.password).encodeToByteArray()).trimEnd('=')
    }

    /** Signs in with a code from [exportAccountCode]. Keeps the old account if it fails. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun importAccountCode(code: String) {
        val raw = code.trim().removePrefix("HISAB1-")
        val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
        val parts = runCatching { Base64.UrlSafe.decode(padded).decodeToString().split("\n") }.getOrNull()
        if (parts == null || parts.size != 2) throw ApiException(400, "That code doesn't look right")
        val creds = Credentials(parts[0], parts[1])
        sessionLock.withLock {
            try {
                login(creds)
            } catch (e: ApiException) {
                throw ApiException(e.status, "That code didn't work. Check it and try again.")
            }
            store.credentials = creds
            store.groups = null
            sessionChecked = true
        }
    }

    // --- reading --------------------------------------------------------------

    suspend fun groups(): Loaded<List<GroupSummary>> {
        return try {
            ensureSession()
            flushOutbox()
            val me = store.userId ?: ""
            // Collection rules only return rows from my groups, so one call each is enough.
            val groups = pb.listAll<GroupDto>("groups", "", "-created")
            val members = pb.listAll<MemberDto>("members", "")
            val expenses = pb.listAll<ExpenseDto>("expenses", "")
            val payments = pb.listAll<PaymentDto>("payments", "")
            val summaries = groups.mapNotNull { g ->
                val gm = members.filter { it.group == g.id }
                val mine = gm.firstOrNull { it.user == me } ?: return@mapNotNull null
                val net = Balances.net(
                    gm.map { it.id },
                    expenses.filter { it.group == g.id }.map { it.toEntry() },
                    payments.filter { it.group == g.id }.map { it.toEntry() },
                )
                GroupSummary(g, mine.id, net[mine.id] ?: 0L, gm.size)
            }
            store.groups = summaries
            Loaded(summaries)
        } catch (e: OfflineException) {
            Loaded(store.groups ?: throw e, offline = true)
        }
    }

    fun cachedGroups(): List<GroupSummary>? = store.groups
    fun cachedGroup(id: String): GroupData? = store.group(id)?.let { withPending(it) }

    suspend fun group(id: String): Loaded<GroupData> {
        return try {
            ensureSession()
            flushOutbox()
            val f = "group = ${PocketBase.q(id)}"
            val group: GroupDto = pb.get("/api/collections/groups/records/$id")
            val members = pb.listAll<MemberDto>("members", f, "created")
            val data = GroupData(
                group = group,
                members = members,
                expenses = pb.listAll("expenses", f, "-date,-created"),
                payments = pb.listAll("payments", f, "-date,-created"),
                recurring = pb.listAll("recurring", f, "next_date"),
                myMemberId = members.firstOrNull { it.user == store.userId }?.id ?: "",
            )
            store.saveGroup(data)
            Loaded(withPending(data))
        } catch (e: OfflineException) {
            Loaded(cachedGroup(id) ?: throw e, offline = true)
        }
    }

    /** Adds outbox items (not yet on the server) to a group's data. */
    private fun withPending(data: GroupData): GroupData {
        val pending = store.outbox.filter { it.groupId == data.group.id }
        if (pending.isEmpty()) return data
        val exps = pending.filter { it.collection == "expenses" }
            .map { HisabJson.decodeFromString(ExpenseDto.serializer(), it.body) }
        val pays = pending.filter { it.collection == "payments" }
            .map { HisabJson.decodeFromString(PaymentDto.serializer(), it.body) }
        return data.copy(
            expenses = (exps + data.expenses.filter { e -> exps.none { it.id == e.id } }).sortedByDescending { it.date },
            payments = (pays + data.payments.filter { p -> pays.none { it.id == p.id } }).sortedByDescending { it.date },
            pendingIds = (exps.map { it.id } + pays.map { it.id }).toSet(),
        )
    }

    // --- groups & members -----------------------------------------------------

    suspend fun createGroup(name: String, currency: String, myName: String, others: List<String>): CreateGroupResponse {
        ensureSession()
        return pb.post(
            "/api/hisab/groups",
            buildJsonObject {
                put("name", name)
                put("currency", currency)
                put("myName", myName)
                putJsonArray("others") { others.forEach { add(JsonPrimitive(it)) } }
            },
        )
    }

    suspend fun invitePreview(code: String): InvitePreview = pb.get("/api/hisab/invite/$code")

    suspend fun join(code: String, memberId: String?, name: String?): JoinResponse {
        ensureSession()
        return pb.post(
            "/api/hisab/join",
            buildJsonObject {
                put("code", code)
                if (memberId != null) put("memberId", memberId) else put("name", name ?: "")
            },
        )
    }

    suspend fun inviteCode(groupId: String): String {
        ensureSession()
        return pb.get<InviteCodeResponse>("/api/hisab/groups/$groupId/invite").inviteCode
    }

    suspend fun resetInvite(groupId: String): String {
        ensureSession()
        return pb.post<InviteCodeResponse>("/api/hisab/groups/$groupId/reset-invite").inviteCode
    }

    fun inviteLink(code: String) = inviteBaseUrl + code

    suspend fun renameGroup(groupId: String, name: String) {
        pb.patch<GroupDto>("/api/collections/groups/records/$groupId", buildJsonObject { put("name", name) })
    }

    suspend fun addMember(groupId: String, name: String): MemberDto {
        ensureSession()
        return pb.post(
            "/api/collections/members/records",
            buildJsonObject {
                put("group", groupId)
                put("name", name)
            },
        )
    }

    suspend fun updateMember(memberId: String, name: String, payHandles: Map<String, String>) {
        ensureSession()
        pb.patch<MemberDto>(
            "/api/collections/members/records/$memberId",
            buildJsonObject {
                put("name", name)
                put("pay_handles", HisabJson.encodeToJsonElement(payHandles.filterValues { it.isNotBlank() }))
            },
        )
    }

    // --- expenses & payments --------------------------------------------------

    /** Saves an expense; when offline it's queued and shown as pending. */
    suspend fun addExpense(
        groupId: String,
        description: String,
        amount: Long,
        paidBy: String,
        shares: List<Share>,
        date: String,
        category: String = "",
        note: String = "",
    ) {
        val body = expenseBody(groupId, description, amount, paidBy, shares, category, note) +
            mapOf("id" to JsonPrimitive(randomId(15)), "date" to JsonPrimitive(date))
        createOrQueue("expenses", groupId, JsonObject(body))
    }

    suspend fun updateExpense(
        expenseId: String,
        groupId: String,
        description: String,
        amount: Long,
        paidBy: String,
        shares: List<Share>,
        date: String,
        category: String = "",
        note: String = "",
    ) {
        ensureSession()
        val body = expenseBody(groupId, description, amount, paidBy, shares, category, note) +
            mapOf("date" to JsonPrimitive(date))
        pb.patch<ExpenseDto>("/api/collections/expenses/records/$expenseId", JsonObject(body - "group"))
    }

    private fun expenseBody(
        groupId: String,
        description: String,
        amount: Long,
        paidBy: String,
        shares: List<Share>,
        category: String,
        note: String,
    ): Map<String, JsonElement> = buildJsonObject {
        put("group", groupId)
        put("description", description)
        put("amount", amount)
        put("paid_by", paidBy)
        putJsonArray("splits") {
            shares.forEach { s -> addJsonObject { put("member", s.memberId); put("amount", s.amount) } }
        }
        put("category", category)
        put("note", note)
    }

    suspend fun deleteExpense(id: String) {
        if (dropPending(id)) return
        ensureSession()
        pb.delete("/api/collections/expenses/records/$id")
    }

    suspend fun addPayment(groupId: String, from: String, to: String, amount: Long, date: String, method: String = "") {
        val body = buildJsonObject {
            put("id", randomId(15))
            put("group", groupId)
            put("from", from)
            put("to", to)
            put("amount", amount)
            put("date", date)
            put("method", method)
        }
        createOrQueue("payments", groupId, body)
    }

    suspend fun deletePayment(id: String) {
        if (dropPending(id)) return
        ensureSession()
        pb.delete("/api/collections/payments/records/$id")
    }

    // --- recurring bills --------------------------------------------------------

    suspend fun addRecurring(
        groupId: String,
        description: String,
        amount: Long,
        paidBy: String,
        shares: List<Share>,
        interval: String,
        firstDate: String,
    ) {
        ensureSession()
        val body = expenseBody(groupId, description, amount, paidBy, shares, "", "") - "note" +
            mapOf(
                "interval" to JsonPrimitive(interval),
                "next_date" to JsonPrimitive(firstDate),
            )
        pb.post<RecurringDto>("/api/collections/recurring/records", JsonObject(body))
    }

    suspend fun setRecurringActive(id: String, active: Boolean) {
        ensureSession()
        pb.patch<RecurringDto>("/api/collections/recurring/records/$id", buildJsonObject { put("active", active) })
    }

    suspend fun deleteRecurring(id: String) {
        ensureSession()
        pb.delete("/api/collections/recurring/records/$id")
    }

    // --- offline outbox ----------------------------------------------------------

    private suspend fun createOrQueue(collection: String, groupId: String, body: JsonObject) {
        try {
            ensureSession()
            pb.send(HttpMethod.Post, "/api/collections/$collection/records", body)
        } catch (e: OfflineException) {
            store.outbox = store.outbox + PendingWrite(collection, groupId, body.toString())
        }
    }

    private fun dropPending(id: String): Boolean {
        val before = store.outbox
        val after = before.filterNot { HisabJson.parseToJsonElement(it.body).let { b -> (b as JsonObject)["id"].toString().trim('"') == id } }
        store.outbox = after
        return after.size != before.size
    }

    /** Number of writes still waiting to be sent. */
    val pendingCount: Int get() = store.outbox.size

    /** Sends queued writes. Stops quietly at the first network error. */
    suspend fun flushOutbox() {
        for (item in store.outbox) {
            try {
                pb.send(
                    HttpMethod.Post,
                    "/api/collections/${item.collection}/records",
                    HisabJson.parseToJsonElement(item.body),
                )
            } catch (e: OfflineException) {
                return
            } catch (e: ApiException) {
                // Already saved by an earlier attempt (same id), or no longer valid
                // (e.g. the group was deleted). Either way, don't retry forever.
            }
            store.outbox = store.outbox.filterNot { it == item }
        }
    }

    fun signOutEverywhereOnThisPhone() = store.clearAll()

    companion object {
        private const val ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

        fun randomId(length: Int): String =
            buildString { repeat(length) { append(ID_ALPHABET[Random.nextInt(ID_ALPHABET.length)]) } }

        /** Pulls an invite code out of a pasted link or message. */
        fun parseInviteCode(text: String): String? {
            val t = text.trim()
            Regex("join/([a-z0-9]{8,32})", RegexOption.IGNORE_CASE).find(t)?.let { return it.groupValues[1].lowercase() }
            Regex("^[a-z0-9]{8,32}$", RegexOption.IGNORE_CASE).find(t)?.let { return it.value.lowercase() }
            return null
        }
    }
}
