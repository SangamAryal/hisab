package app.hisab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hisab.core.Dates
import app.hisab.core.Money
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.launch

/** Payment apps a member can list, and how to open each one. */
data class PayApp(val key: String, val label: String, val hint: String)

val payApps = listOf(
    PayApp("esewa", "eSewa", "eSewa ID (mobile number)"),
    PayApp("khalti", "Khalti", "Khalti ID (mobile number)"),
    PayApp("upi", "UPI", "UPI ID, e.g. name@okbank"),
    PayApp("paypal", "PayPal", "PayPal.me username"),
    PayApp("venmo", "Venmo", "Venmo username"),
    PayApp("bank", "Bank / other", "Bank account or anything else"),
)

/** A link that opens the payment app with the amount filled in, where the app supports it. */
fun payLink(app: String, handle: String, name: String, amount: Long, currency: String): String? {
    val amt = Money.toInput(amount, currency)
    return when (app) {
        "upi" -> "upi://pay?pa=${handle.encodeURLParameter()}&pn=${name.encodeURLParameter()}&am=$amt&cu=$currency"
        "paypal" -> "https://paypal.me/${handle.trim().removePrefix("@").encodeURLParameter()}/$amt$currency"
        "venmo" -> "https://venmo.com/${handle.trim().removePrefix("@").encodeURLParameter()}?txn=pay&amount=$amt"
        else -> null
    }
}

@Suppress("DEPRECATION") // LocalClipboardManager: simple and still supported on all targets.
@Composable
fun SettleScreen(groupId: String, initialFrom: String, initialTo: String, initialAmount: Long, onDone: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val data = remember { repo.cachedGroup(groupId) }
    if (data == null) {
        Loading()
        return
    }
    val cur = data.group.currency
    var from by remember { mutableStateOf(initialFrom.ifBlank { data.myMemberId }) }
    var to by remember { mutableStateOf(initialTo) }
    var amountText by remember { mutableStateOf(if (initialAmount > 0) Money.toInput(initialAmount, cur) else "") }
    var date by remember { mutableStateOf(Dates.today()) }
    var method by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = Money.parse(amountText, cur)

    Scaffold(topBar = { BackBar("Settle up", onDone) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text("Who paid?", style = MaterialTheme.typography.titleSmall)
            MemberChips(data.members, data.myMemberId, from, { from = it; if (to == it) to = "" })
            Spacer(Modifier.height(12.dp))
            Text("Paid to", style = MaterialTheme.typography.titleSmall)
            MemberChips(data.members, data.myMemberId, to, { to = it }, exclude = from)
            Spacer(Modifier.height(12.dp))
            AmountField(amountText, { amountText = it }, cur, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            DateField(date, { date = it })

            val receiver = data.members.firstOrNull { it.id == to }
            val handles = receiver?.payHandles.orEmpty().filterValues { it.isNotBlank() }
            if (receiver != null && from == data.myMemberId && handles.isNotEmpty()) {
                Text(
                    "Pay ${receiver.name} with",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                )
                payApps.filter { handles.containsKey(it.key) }.forEach { app ->
                    val handle = handles.getValue(app.key)
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold)
                                Text(handle, style = MaterialTheme.typography.bodySmall)
                            }
                            val link = amount?.let { payLink(app.key, handle, receiver.name, it, cur) }
                            if (link != null) {
                                FilledTonalButton(onClick = {
                                    method = app.label
                                    runCatching { uri.openUri(link) }.onFailure { error = "Couldn't open ${app.label} on this phone" }
                                }) { Text("Open") }
                            } else {
                                TextButton(onClick = {
                                    method = app.label
                                    clipboard.setText(AnnotatedString(handle))
                                }) { Text("Copy") }
                            }
                        }
                    }
                }
                Text(
                    "After you've paid, come back and tap Record payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && amount != null && to.isNotBlank() && from != to,
                onClick = {
                    val a = amount ?: return@Button
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            repo.addPayment(groupId, from, to, a, Dates.toPb(date), method)
                            DataVersion.bump()
                            onDone()
                        } catch (e: Exception) {
                            error = e.friendly()
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Saving…" else "Record payment") }
        }
    }
}
