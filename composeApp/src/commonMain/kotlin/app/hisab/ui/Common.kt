package app.hisab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisab.core.Money
import app.hisab.data.ApiException
import app.hisab.data.OfflineException
import app.hisab.data.Repository
import kotlin.math.abs

val LocalRepository = compositionLocalOf<Repository> { error("No repository") }

/** A message a person can act on, for any error. */
fun Throwable.friendly(): String = when (this) {
    is OfflineException -> "You're offline. Check your internet and try again."
    is ApiException -> message ?: "Something went wrong"
    is IllegalArgumentException -> message ?: "Please check what you entered"
    else -> "Something went wrong. Please try again."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
    )
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun OfflineBanner(pending: Int) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            if (pending > 0) "Offline. $pending change${if (pending == 1) "" else "s"} will sync when you're back online."
            else "Offline. Showing what was saved on this phone.",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Circle with a person's initials; color picked from their name so it stays stable. */
@Composable
fun Avatar(name: String, size: Dp = 40.dp) {
    val palette = listOf(
        Color(0xFF0F766E), Color(0xFFB45309), Color(0xFF7C3AED), Color(0xFFBE185D),
        Color(0xFF1D4ED8), Color(0xFF15803D), Color(0xFFC2410C), Color(0xFF4D7C0F),
    )
    val color = palette[abs(name.hashCode()) % palette.size]
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
    }
}

/** "you are owed Rs 400" / "you owe Rs 100" / "settled up" with matching color. */
@Composable
fun BalanceLine(balance: Long, currency: String, you: Boolean = true) {
    val c = LocalMoneyColors.current
    val (text, color) = when {
        balance > 0 -> (if (you) "you are owed " else "is owed ") + Money.format(balance, currency) to c.owed
        balance < 0 -> (if (you) "you owe " else "owes ") + Money.format(-balance, currency) to c.owes
        else -> "settled up" to c.settled
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
fun RowSpaced(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}
