package app.hisab.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisab.core.Balances
import app.hisab.core.Categories
import app.hisab.core.Dates
import app.hisab.core.Money
import app.hisab.core.Transfer
import app.hisab.data.ExpenseDto
import app.hisab.data.GroupData
import app.hisab.data.PaymentDto
import app.hisab.data.toEntry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    onSettle: (from: String, to: String, amount: Long) -> Unit,
    onAddRecurring: () -> Unit,
    onEditMember: (String) -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val share = rememberShareText()
    val version by DataVersion.tick.collectAsState()
    var data by remember { mutableStateOf(repo.cachedGroup(groupId)) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var deletePaymentPrompt by remember { mutableStateOf<PaymentDto?>(null) }

    suspend fun load() {
        try {
            val r = repo.group(groupId)
            data = r.data
            offline = r.offline
            error = null
        } catch (e: Exception) {
            if (data == null) error = e.friendly()
        }
    }
    LaunchedEffect(version) { load() }

    fun invite() {
        val d = data ?: return
        scope.launch {
            try {
                val code = repo.inviteCode(groupId)
                share(
                    "Join \"${d.group.name}\" on Hisab to split our costs: ${repo.inviteLink(code)}\n\n" +
                        "Or open Hisab, tap Join and enter the code $code",
                )
            } catch (e: Exception) {
                message = e.friendly()
            }
        }
    }

    Scaffold(
        topBar = {
            BackBar(data?.group?.name ?: "", onBack) {
                IconButton(onClick = ::invite) { Icon(Icons.Default.Share, contentDescription = "Invite people") }
            }
        },
        floatingActionButton = {
            if (data != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add expense") },
                )
            }
        },
    ) { padding ->
        val d = data
        when {
            d == null && error != null -> Column(Modifier.padding(padding)) { ErrorState(error!!) { scope.launch { load() } } }
            d == null -> Loading(Modifier.padding(padding))
            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                if (offline) OfflineBanner(repo.pendingCount)
                val net = remember(d) {
                    Balances.net(d.members.map { it.id }, d.expenses.map { it.toEntry() }, d.payments.map { it.toEntry() })
                }
                val transfers = remember(net) { Balances.simplify(net) }
                Header(d, net[d.myMemberId] ?: 0L, transfers, onSettle, onInvite = ::invite)
                PrimaryTabRow(selectedTabIndex = tab) {
                    listOf("Activity", "Balances", "People & bills").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = { scope.launch { refreshing = true; load(); refreshing = false } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        when (tab) {
                            0 -> activity(d, onEditExpense) { p -> deletePaymentPrompt = p }
                            1 -> balances(d, net, transfers, onSettle)
                            else -> people(d, onEditMember, onAddRecurring, onInvite = ::invite) { id, active ->
                                scope.launch {
                                    try {
                                        repo.setRecurringActive(id, active)
                                        DataVersion.bump()
                                    } catch (e: Exception) {
                                        message = e.friendly()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deletePaymentPrompt?.let { p ->
        val d = data ?: return@let
        AlertDialog(
            onDismissRequest = { deletePaymentPrompt = null },
            title = { Text("Delete this payment?") },
            text = { Text("${d.nameOf(p.from)} paid ${d.nameOf(p.to)} ${Money.format(p.amount, d.group.currency)}. Deleting it changes everyone's balances.") },
            confirmButton = {
                TextButton(onClick = {
                    deletePaymentPrompt = null
                    scope.launch {
                        try {
                            repo.deletePayment(p.id)
                            DataVersion.bump()
                        } catch (e: Exception) {
                            message = e.friendly()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletePaymentPrompt = null }) { Text("Keep") } },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

fun GroupData.nameOf(memberId: String): String =
    if (memberId == myMemberId) "You" else members.firstOrNull { it.id == memberId }?.name ?: "Someone"

@Composable
private fun Header(
    d: GroupData,
    myBalance: Long,
    transfers: List<Transfer>,
    onSettle: (String, String, Long) -> Unit,
    onInvite: () -> Unit,
) {
    val cur = d.group.currency
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                when {
                    myBalance > 0 -> "You are owed"
                    myBalance < 0 -> "You owe"
                    else -> "You're settled up"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (myBalance != 0L) {
                Text(
                    Money.format(kotlin.math.abs(myBalance), cur),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mine = transfers.firstOrNull { it.from == d.myMemberId }
                    ?: transfers.firstOrNull { it.to == d.myMemberId }
                Button(onClick = {
                    if (mine != null) onSettle(mine.from, mine.to, mine.amount) else onSettle(d.myMemberId, "", 0)
                }) { Text("Settle up") }
                if (d.members.count { it.user.isNotEmpty() } < d.members.size || d.members.size < 2) {
                    OutlinedButton(onClick = onInvite) { Text("Invite") }
                }
            }
        }
    }
}

private fun LazyListScope.activity(d: GroupData, onEdit: (String) -> Unit, onPaymentClick: (PaymentDto) -> Unit) {
    val cur = d.group.currency
    val rows: List<Pair<String, Any>> =
        (d.expenses.map { it.date to it as Any } + d.payments.map { it.date to it as Any }).sortedByDescending { it.first }
    if (rows.isEmpty()) {
        item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🧾", fontSize = 40.sp)
                Text("No expenses yet", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Add the first one: groceries, rent, a taxi…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
    var lastMonth = ""
    rows.forEach { (date, row) ->
        val month = Dates.monthLabel(date)
        if (month != lastMonth) {
            lastMonth = month
            item(key = "m-$month") { SectionLabel(month) }
        }
        when (row) {
            is ExpenseDto -> item(key = row.id) {
                ExpenseRow(d, row, cur, pending = row.id in d.pendingIds) { if (row.id !in d.pendingIds) onEdit(row.id) }
            }
            is PaymentDto -> item(key = row.id) {
                PaymentRow(d, row, cur, pending = row.id in d.pendingIds) { onPaymentClick(row) }
            }
        }
    }
}

@Composable
private fun ExpenseRow(d: GroupData, e: ExpenseDto, cur: String, pending: Boolean, onClick: () -> Unit) {
    val c = LocalMoneyColors.current
    val myShare = e.splits.firstOrNull { it.member == d.myMemberId }?.amount ?: 0L
    val iPaid = e.paidBy == d.myMemberId
    val (label, amount, color) = when {
        iPaid && e.amount - myShare > 0 -> Triple("you lent", e.amount - myShare, c.owed)
        !iPaid && myShare > 0 -> Triple("you borrowed", myShare, c.owes)
        else -> Triple("not involved", 0L, c.settled)
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Categories.byId(e.category).emoji, fontSize = 26.sp, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(e.description, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${d.nameOf(e.paidBy)} paid ${Money.format(e.amount, cur)} · ${Dates.display(e.date)}" +
                    (if (e.recurring.isNotEmpty()) " · repeats" else "") +
                    (if (pending) " · waiting to sync" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            if (amount > 0) Text(Money.format(amount, cur), color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PaymentRow(d: GroupData, p: PaymentDto, cur: String, pending: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("💸", fontSize = 26.sp, modifier = Modifier.width(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${d.nameOf(p.from)} paid ${if (p.to == d.myMemberId) "you" else d.nameOf(p.to)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                Dates.display(p.date) + (if (p.method.isNotBlank()) " · ${p.method}" else "") + (if (pending) " · waiting to sync" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(Money.format(p.amount, cur), fontWeight = FontWeight.SemiBold)
    }
}

private fun LazyListScope.balances(
    d: GroupData,
    net: Map<String, Long>,
    transfers: List<Transfer>,
    onSettle: (String, String, Long) -> Unit,
) {
    val cur = d.group.currency
    item { SectionLabel("Who's up, who's down") }
    items(d.members, key = { "b-" + it.id }) { m ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (m.id == d.myMemberId) "${m.name} (you)" else m.name, style = MaterialTheme.typography.bodyLarge)
                BalanceLine(net[m.id] ?: 0L, cur, you = false)
            }
        }
    }
    item { SectionLabel("Simplest way to settle") }
    if (transfers.isEmpty()) {
        item { Text("Everyone is settled up 🎉", modifier = Modifier.padding(horizontal = 16.dp)) }
    }
    items(transfers, key = { "t-${it.from}-${it.to}" }) { t ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${d.nameOf(t.from)} → ${d.nameOf(t.to)}", style = MaterialTheme.typography.bodyLarge)
                Text(Money.format(t.amount, cur), fontWeight = FontWeight.SemiBold)
            }
            FilledTonalButton(onClick = { onSettle(t.from, t.to, t.amount) }) { Text("Settle") }
        }
    }
}

private fun LazyListScope.people(
    d: GroupData,
    onEditMember: (String) -> Unit,
    onAddRecurring: () -> Unit,
    onInvite: () -> Unit,
    onToggleRecurring: (String, Boolean) -> Unit,
) {
    val cur = d.group.currency
    item { SectionLabel("People") }
    items(d.members, key = { "p-" + it.id }) { m ->
        Row(
            Modifier.fillMaxWidth().clickable { onEditMember(m.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(m.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (m.id == d.myMemberId) "${m.name} (you)" else m.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        m.id == d.myMemberId -> "Tap to add how people can pay you"
                        m.user.isNotEmpty() -> "Joined"
                        else -> "Not joined yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddPersonButton(d.group.id)
            OutlinedButton(onClick = onInvite) { Text("Send invite link") }
        }
    }
    item { HorizontalDivider(Modifier.padding(top = 12.dp)) }
    item { SectionLabel("Repeating bills") }
    if (d.recurring.isEmpty()) {
        item {
            Text(
                "Rent, Wi-Fi, electricity… add them once and Hisab adds them every month for you.",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(d.recurring, key = { "r-" + it.id }) { r ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.description, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${Money.format(r.amount, cur)} ${r.interval} · next ${Dates.display(r.nextDate)} · ${d.nameOf(r.paidBy)} pays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = r.active, onCheckedChange = { onToggleRecurring(r.id, it) })
        }
    }
    item {
        AssistChip(
            onClick = onAddRecurring,
            label = { Text("Add a repeating bill") },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AddPersonButton(groupId: String) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { open = true }) { Text("Add person") }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Add a person") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            repo.addMember(groupId, name.trim())
                            name = ""
                            open = false
                            DataVersion.bump()
                        } catch (e: Exception) {
                            error = e.friendly()
                        }
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
