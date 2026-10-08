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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Suppress("DEPRECATION") // LocalClipboardManager
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val share = rememberShareText()
    var showCode by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackBar("Your account", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Text(
                "No sign-up needed: your groups are tied to this phone. To use Hisab on a new phone, or to keep your groups if you reinstall, save your transfer code somewhere safe.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Transfer code", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Anyone with this code can see and edit your groups. Only share it with yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val code = repo.exportAccountCode()
                    if (code == null) {
                        Text("Open a group first, then come back here.", modifier = Modifier.padding(top = 8.dp))
                    } else if (!showCode) {
                        OutlinedButton(onClick = { showCode = true }, modifier = Modifier.padding(top = 8.dp)) { Text("Show my code") }
                    } else {
                        Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 8.dp))
                        Row {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(code)); message = "Copied" }) { Text("Copy") }
                            TextButton(onClick = { share(code) }) { Text("Save / send to myself") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Moving from another phone?", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.trim() },
                label = { Text("Paste your transfer code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                "This replaces the groups on this phone with the ones from your code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                enabled = !busy && input.startsWith("HISAB1-"),
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            repo.importAccountCode(input)
                            DataVersion.bump()
                            message = "Done. Your groups are on this phone now."
                            input = ""
                        } catch (e: Exception) {
                            message = e.friendly()
                        } finally {
                            busy = false
                        }
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Use this code") }

            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp)) }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))
            Text("Hisab is free and open source. No ads, no limits.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { uri.openUri("https://github.com/SangamAryal/hisab") }) { Text("See the code on GitHub") }
        }
    }
}
