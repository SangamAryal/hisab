package app.hisab.ui

import androidx.compose.runtime.Composable

/** Opens the system share sheet with [text]. */
@Composable
expect fun rememberShareText(): (String) -> Unit

/** The currency of the phone's region (e.g. "NPR" in Nepal), if known. */
expect fun deviceCurrencyCode(): String?
