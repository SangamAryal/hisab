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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisab.core.Money
import app.hisab.data.GroupSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onJoin: () -> Unit,
    onAccount: () -> Unit,
) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val version by DataVersion.tick.collectAsState()
    var groups by remember { mutableStateOf(repo.cachedGroups()) }
    var offline by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            val r = repo.groups()
            groups = r.data
            offline = r.offline
            error = null
        } catch (e: Exception) {
            if (groups == null) error = e.friendly()
        }
    }
    LaunchedEffect(version) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hisab", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onJoin) { Text("Join") }
                    IconButton(onClick = onAccount) { Icon(Icons.Default.Person, contentDescription = "Account") }
                },
            )
        },
        floatingActionButton = {
            if (!groups.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreateGroup,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New group") },
                )
            }
        },
    ) { padding ->
        val list = groups
        when {
            list == null && error != null -> Column(Modifier.padding(padding)) {
                ErrorState(error!!) { scope.launch { load() } }
            }
            list == null -> Loading(Modifier.padding(padding))
            list.isEmpty() -> EmptyHome(Modifier.padding(padding), onCreateGroup, onJoin)
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        load()
                        refreshing = false
                    }
                },
                modifier = Modifier.padding(padding),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    if (offline) item { OfflineBanner(repo.pendingCount) }
                    item { Totals(list) }
                    items(list, key = { it.group.id }) { g -> GroupCard(g) { onOpenGroup(g.group.id) } }
                }
            }
        }
    }
}

/** "Overall: you are owed Rs 1,200 · you owe $5.00" (one line per currency). */
@Composable
private fun Totals(groups: List<GroupSummary>) {
    val byCurrency = groups.groupBy { it.group.currency }.mapValues { (_, gs) -> gs.sumOf { it.myBalance } }
        .filterValues { it != 0L }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text("Overall", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (byCurrency.isEmpty()) {
            Text("You're all settled up", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        } else {
            val c = LocalMoneyColors.current
            byCurrency.forEach { (cur, total) ->
                Text(
                    if (total > 0) "You are owed ${Money.format(total, cur)}" else "You owe ${Money.format(-total, cur)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (total > 0) c.owed else c.owes,
                )
            }
        }
    }
}

@Composable
private fun GroupCard(g: GroupSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(g.group.emoji.ifBlank { "👥" }, fontSize = 30.sp, modifier = Modifier.width(48.dp))
            Column(Modifier.weight(1f)) {
                Text(g.group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${g.memberCount} people · ${g.group.currency}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                BalanceLine(g.myBalance, g.group.currency)
            }
        }
    }
}

@Composable
private fun EmptyHome(modifier: Modifier, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🪙", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Split costs, stay friends",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Rent with flatmates, a trip with friends, dinners with your partner. Add what you spend and Hisab keeps track of who owes whom.\n\nFree, no ads, no daily limits.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Start a group") }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onJoin, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("I have an invite link") }
        Spacer(Modifier.size(48.dp))
    }
}
