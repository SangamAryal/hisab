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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.hisab.core.Dates
import app.hisab.core.Money
import kotlinx.coroutines.launch

@Composable
fun RecurringScreen(groupId: String, onDone: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val data = remember { repo.cachedGroup(groupId) }
    if (data == null) {
        Loading()
        return
    }
    val cur = data.group.currency
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(data.myMemberId) }
    var interval by remember { mutableStateOf("monthly") }
    var first by remember { mutableStateOf(Dates.today()) }
    val split = remember { SplitState(data.members, null, cur) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amount = Money.parse(amountText, cur)

    Scaffold(topBar = { BackBar("Repeating bill", onDone) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(120) },
                label = { Text("Bill") },
                placeholder = { Text("Rent, Wi-Fi, electricity…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            AmountField(amountText, { amountText = it }, cur, modifier = Modifier.fillMaxWidth())
            Text("Repeats", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            Row {
                listOf("monthly" to "Every month", "weekly" to "Every week").forEach { (v, label) ->
                    FilterChip(selected = interval == v, onClick = { interval = v }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            DateField(first, { first = it }, label = "First one on")
            Text("Paid by", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            MemberChips(data.members, data.myMemberId, paidBy, { paidBy = it })
            Text("Split", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            SplitEditor(split, data.members, data.myMemberId, cur, amount)

            Text(
                "Hisab adds this bill on its date automatically. Turn it off any time under People & bills.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && description.isNotBlank() && amount != null,
                onClick = {
                    val a = amount ?: return@Button
                    val shares = split.compute(a, cur).getOrElse {
                        error = it.message
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            repo.addRecurring(groupId, description.trim(), a, paidBy, shares, interval, Dates.toPb(first))
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
            ) { Text(if (busy) "Saving…" else "Save bill") }
        }
    }
}
