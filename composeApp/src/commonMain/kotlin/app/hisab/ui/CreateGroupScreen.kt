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
import app.hisab.core.Money
import kotlinx.coroutines.launch

private val groupKinds = listOf("🏠" to "Home", "✈️" to "Trip", "❤️" to "Couple", "🍽️" to "Food", "👥" to "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(onBack: () -> Unit, onCreated: (String) -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🏠") }
    val defaultCurrency = remember { deviceCurrencyCode()?.takeIf { it.length == 3 } ?: "USD" }
    var currency by remember { mutableStateOf(defaultCurrency) }
    var otherCurrency by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("") }
    var friends by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val currencies = remember { (listOf(defaultCurrency) + Money.popular).distinct().take(12) }

    Scaffold(topBar = { BackBar("New group", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                label = { Text("Group name") },
                placeholder = { Text("Flat 4B, Pokhara trip…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            FlowRow {
                groupKinds.forEach { (e, label) ->
                    FilterChip(
                        selected = emoji == e,
                        onClick = { emoji = e },
                        label = { Text("$e $label") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Text("Currency", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            FlowRow {
                currencies.forEach { c ->
                    FilterChip(
                        selected = currency == c && otherCurrency.isBlank(),
                        onClick = { currency = c; otherCurrency = "" },
                        label = { Text(c) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
            OutlinedTextField(
                value = otherCurrency,
                onValueChange = { otherCurrency = it.filter(Char::isLetter).take(3).uppercase() },
                label = { Text("Other currency code (optional)") },
                placeholder = { Text("e.g. QAR") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = myName,
                onValueChange = { myName = it.take(60) },
                label = { Text("Your name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = friends,
                onValueChange = { friends = it },
                label = { Text("Who else? (one name per line)") },
                supportingText = { Text("They don't need the app yet. You can invite them later.") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !busy && name.isNotBlank() && myName.isNotBlank(),
                onClick = {
                    val cur = otherCurrency.ifBlank { currency }
                    if (cur.length != 3) {
                        error = "Currency codes have 3 letters, like NPR or USD"
                        return@Button
                    }
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val others = friends.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
                            val r = repo.createGroup(name.trim(), cur, myName.trim(), others)
                            DataVersion.bump()
                            onCreated(r.groupId)
                        } catch (e: Exception) {
                            error = e.friendly()
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (busy) "Creating…" else "Create group") }
        }
    }
}
