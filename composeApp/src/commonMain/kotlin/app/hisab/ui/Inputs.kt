package app.hisab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hisab.core.Dates
import app.hisab.core.Money
import app.hisab.core.Share
import app.hisab.core.Splits
import app.hisab.data.MemberDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberChips(
    members: List<MemberDto>,
    myMemberId: String,
    selected: String,
    onSelect: (String) -> Unit,
    exclude: String? = null,
) {
    FlowRow {
        members.filter { it.id != exclude }.forEach { m ->
            FilterChip(
                selected = selected == m.id,
                onClick = { onSelect(m.id) },
                label = { Text(if (m.id == myMemberId) "You" else m.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, currency: String, label: String = "Amount", modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }.take(16)) },
        label = { Text(label) },
        prefix = { Text(Money.symbol(currency) + " ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun DateField(date: LocalDate, onChange: (LocalDate) -> Unit, label: String = "Date") {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text("$label: ${Dates.display(Dates.toPb(date))}") }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

enum class SplitMode(val label: String) { EQUAL("Equally"), EXACT("Exact amounts"), SHARES("By shares") }

/** Holds the "how is it split" choices for an expense. */
class SplitState(members: List<MemberDto>, initial: List<Share>?, currency: String) {
    var mode by mutableStateOf(SplitMode.EQUAL)
    val included = mutableStateMapOf<String, Boolean>()
    val exact = mutableStateMapOf<String, String>()
    val shares = mutableStateMapOf<String, String>()

    init {
        members.forEach { m ->
            included[m.id] = initial == null || initial.any { it.memberId == m.id && it.amount > 0 }
            shares[m.id] = "1"
            exact[m.id] = initial?.firstOrNull { it.memberId == m.id }?.let { Money.toInput(it.amount, currency) } ?: ""
        }
        if (initial != null) {
            val positive = initial.filter { it.amount > 0 }
            val amount = positive.sumOf { it.amount }
            val isEqual = positive.isNotEmpty() && Splits.equal(amount, positive.map { it.memberId })
                .map { it.amount }.sorted() == positive.map { it.amount }.sorted()
            if (!isEqual) mode = SplitMode.EXACT
        }
    }

    /** The shares for [amount], or an error message to show. */
    fun compute(amount: Long, currency: String): Result<List<Share>> = runCatching {
        when (mode) {
            SplitMode.EQUAL -> {
                val ids = included.filterValues { it }.keys.toList()
                require(ids.isNotEmpty()) { "Pick at least one person to split with" }
                Splits.equal(amount, ids)
            }
            SplitMode.EXACT -> {
                val list = exact.mapNotNull { (id, v) ->
                    if (v.isBlank()) null else Share(id, Money.parse(v, currency) ?: throw IllegalArgumentException("Check the amount for each person"))
                }
                val total = list.sumOf { it.amount }
                require(total == amount) {
                    val diff = amount - total
                    if (diff > 0) "${Money.format(diff, currency)} still to assign" else "${Money.format(-diff, currency)} too much assigned"
                }
                list
            }
            SplitMode.SHARES -> {
                val weights = shares.mapValues { it.value.toIntOrNull() ?: 0 }
                require(weights.values.any { it > 0 }) { "Give at least one person a share" }
                Splits.weighted(amount, weights)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitEditor(state: SplitState, members: List<MemberDto>, myMemberId: String, currency: String, amount: Long?) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        SplitMode.entries.forEachIndexed { i, mode ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { state.mode = mode },
                shape = SegmentedButtonDefaults.itemShape(i, SplitMode.entries.size),
                label = { Text(mode.label, maxLines = 1) },
            )
        }
    }
    val preview = amount?.let { a -> state.compute(a, currency) }
    Column(Modifier.padding(top = 8.dp)) {
        members.forEach { m ->
            val name = if (m.id == myMemberId) "You" else m.name
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                when (state.mode) {
                    SplitMode.EQUAL -> {
                        Checkbox(checked = state.included[m.id] == true, onCheckedChange = { state.included[m.id] = it })
                        Text(name, Modifier.weight(1f))
                        val share = preview?.getOrNull()?.firstOrNull { it.memberId == m.id }?.amount
                        if (share != null && share > 0) Text(Money.format(share, currency), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SplitMode.EXACT -> {
                        Text(name, Modifier.weight(1f))
                        AmountField(state.exact[m.id] ?: "", { state.exact[m.id] = it }, currency, label = "", modifier = Modifier.width(160.dp))
                    }
                    SplitMode.SHARES -> {
                        Text(name, Modifier.weight(1f))
                        OutlinedTextField(
                            value = state.shares[m.id] ?: "",
                            onValueChange = { v -> state.shares[m.id] = v.filter(Char::isDigit).take(3) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        val share = preview?.getOrNull()?.firstOrNull { it.memberId == m.id }?.amount ?: 0
                        Text(Money.format(share, currency), Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        preview?.exceptionOrNull()?.let {
            Text(it.message ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
