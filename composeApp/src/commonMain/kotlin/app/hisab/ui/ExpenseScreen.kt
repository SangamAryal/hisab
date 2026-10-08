package app.hisab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.hisab.core.Categories
import app.hisab.core.Dates
import app.hisab.core.Money
import app.hisab.core.Share
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseScreen(groupId: String, expenseId: String?, onDone: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val data = remember { repo.cachedGroup(groupId) }
    if (data == null) {
        Loading()
        return
    }
    val cur = data.group.currency
    val existing = remember { expenseId?.let { id -> data.expenses.firstOrNull { it.id == id } } }

    var description by remember { mutableStateOf(existing?.description ?: "") }
    var amountText by remember { mutableStateOf(existing?.let { Money.toInput(it.amount, cur) } ?: "") }
    var paidBy by remember { mutableStateOf(existing?.paidBy ?: data.myMemberId) }
    var date by remember { mutableStateOf(existing?.let { Dates.fromPb(it.date) } ?: Dates.today()) }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var categoryTouched by remember { mutableStateOf(existing != null) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val split = remember { SplitState(data.members, existing?.splits?.map { Share(it.member, it.amount) }, cur) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val amount = Money.parse(amountText, cur)

    Scaffold(
        topBar = {
            BackBar(if (existing == null) "Add expense" else "Edit expense", onDone) {
                if (existing != null) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it.take(120)
                    if (!categoryTouched) category = Categories.guess(it)?.id ?: ""
                },
                label = { Text("What was it for?") },
                placeholder = { Text("Groceries, rent, momo…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            AmountField(amountText, { amountText = it }, cur, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            FlowRow {
                Categories.all.forEach { c ->
                    FilterChip(
                        selected = category == c.id,
                        onClick = { category = c.id; categoryTouched = true },
                        label = { Text("${c.emoji} ${c.label}") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Text("Paid by", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            MemberChips(data.members, data.myMemberId, paidBy, { paidBy = it })

            Text("Split", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            SplitEditor(split, data.members, data.myMemberId, cur, amount)

            Spacer(Modifier.height(12.dp))
            DateField(date, { date = it })
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
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
                            if (existing == null) {
                                repo.addExpense(groupId, description.trim(), a, paidBy, shares, Dates.toPb(date), category, note.trim())
                            } else {
                                repo.updateExpense(existing.id, groupId, description.trim(), a, paidBy, shares, Dates.toPb(date), category, note.trim())
                            }
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
            ) { Text(if (busy) "Saving…" else "Save") }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${existing.description}\"?") },
            text = { Text("This changes everyone's balances.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            repo.deleteExpense(existing.id)
                            DataVersion.bump()
                            onDone()
                        } catch (e: Exception) {
                            error = e.friendly()
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}
