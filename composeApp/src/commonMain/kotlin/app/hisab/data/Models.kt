package app.hisab.data

import app.hisab.core.ExpenseEntry
import app.hisab.core.PaymentEntry
import app.hisab.core.Share
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire models: field names match the PocketBase collections in backend/pb_migrations.

@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
data class AuthResponse(val token: String, val record: UserDto)

@Serializable
data class UserDto(val id: String, val email: String = "", val name: String = "")

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val currency: String,
    val emoji: String = "",
    @SerialName("created_by") val createdBy: String = "",
    val created: String = "",
)

@Serializable
data class MemberDto(
    val id: String,
    val group: String,
    val name: String,
    val user: String = "",
    @SerialName("pay_handles") val payHandles: Map<String, String>? = null,
)

@Serializable
data class SplitDto(val member: String, val amount: Long)

@Serializable
data class ExpenseDto(
    val id: String,
    val group: String,
    val description: String,
    val amount: Long,
    @SerialName("paid_by") val paidBy: String,
    val splits: List<SplitDto> = emptyList(),
    val category: String = "",
    val date: String,
    val note: String = "",
    val recurring: String = "",
    val created: String = "",
)

@Serializable
data class PaymentDto(
    val id: String,
    val group: String,
    val from: String,
    val to: String,
    val amount: Long,
    val date: String,
    val method: String = "",
    val created: String = "",
)

@Serializable
data class RecurringDto(
    val id: String,
    val group: String,
    val description: String,
    val amount: Long,
    @SerialName("paid_by") val paidBy: String,
    val splits: List<SplitDto> = emptyList(),
    val interval: String,
    @SerialName("next_date") val nextDate: String,
    val active: Boolean = true,
)

@Serializable
data class CreateGroupResponse(val groupId: String, val memberId: String, val inviteCode: String)

@Serializable
data class JoinResponse(val groupId: String, val memberId: String)

@Serializable
data class InviteCodeResponse(val inviteCode: String)

@Serializable
data class InvitePreview(val group: InviteGroup, val members: List<InviteMember>)

@Serializable
data class InviteGroup(val id: String, val name: String, val currency: String, val emoji: String = "")

@Serializable
data class InviteMember(val id: String, val name: String, val claimed: Boolean)

/** Everything needed to draw one group. */
@Serializable
data class GroupData(
    val group: GroupDto,
    val members: List<MemberDto>,
    val expenses: List<ExpenseDto>,
    val payments: List<PaymentDto>,
    val recurring: List<RecurringDto>,
    val myMemberId: String,
    /** Expense/payment ids saved on this phone but not yet sent to the server. */
    val pendingIds: Set<String> = emptySet(),
)

/** A group on the home screen with my own balance in it. */
@Serializable
data class GroupSummary(
    val group: GroupDto,
    val myMemberId: String,
    val myBalance: Long,
    val memberCount: Int,
)

fun ExpenseDto.toEntry() = ExpenseEntry(paidBy, amount, splits.map { Share(it.member, it.amount) })
fun PaymentDto.toEntry() = PaymentEntry(from, to, amount)
