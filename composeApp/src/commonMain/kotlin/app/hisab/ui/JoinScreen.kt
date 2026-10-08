package app.hisab.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisab.data.InvitePreview
import app.hisab.data.Repository
import kotlinx.coroutines.launch

@Composable
fun JoinScreen(initialCode: String, onBack: () -> Unit, onJoined: (String) -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(initialCode) }
    var preview by remember { mutableStateOf<InvitePreview?>(null) }
    var code by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun lookUp(text: String) {
        val c = Repository.parseInviteCode(text)
        if (c == null) {
            error = "Paste the invite link or the code your friend sent you"
            return
        }
        busy = true
        error = null
        scope.launch {
            try {
                preview = repo.invitePreview(c)
                code = c
            } catch (e: Exception) {
                error = e.friendly()
            } finally {
                busy = false
            }
        }
    }

    fun join(memberId: String?, name: String?) {
        busy = true
        error = null
        scope.launch {
            try {
                val r = repo.join(code, memberId, name)
                DataVersion.bump()
                onJoined(r.groupId)
            } catch (e: Exception) {
                error = e.friendly()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(initialCode) { if (initialCode.isNotBlank()) lookUp(initialCode) }

    Scaffold(topBar = { BackBar("Join a group", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            val p = preview
            if (p == null) {
                Text("Paste the invite link or code", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Link or code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = !busy && input.isNotBlank(),
                    onClick = { lookUp(input) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (busy) "Looking…" else "Continue") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.group.emoji.ifBlank { "👥" }, fontSize = 36.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(p.group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("${p.members.size} people · ${p.group.currency}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val open = p.members.filter { !it.claimed }
                if (open.isNotEmpty()) {
                    Text("Which one is you?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
                    open.forEach { m ->
                        Card(
                            onClick = { if (!busy) join(m.id, null) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(m.name)
                                Spacer(Modifier.width(12.dp))
                                Text("I'm ${m.name}", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Text(
                    if (open.isEmpty()) "Your name" else "Not on the list?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it.take(60) },
                    label = { Text("Your name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !busy && newName.isNotBlank(),
                    onClick = { join(null, newName.trim()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Join as ${newName.trim().ifBlank { "…" }}") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}
