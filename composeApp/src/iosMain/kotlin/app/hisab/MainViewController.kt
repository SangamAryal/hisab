package app.hisab

import androidx.compose.ui.window.ComposeUIViewController
import app.hisab.ui.App
import app.hisab.ui.DeepLinks

fun MainViewController() = ComposeUIViewController { App() }

/** Called from Swift's onOpenURL for hisab://join/<code> links. */
fun handleDeepLink(url: String) = DeepLinks.handle(url)
