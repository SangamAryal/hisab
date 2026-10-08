package app.hisab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareText(): (String) -> Unit = remember {
    { text ->
        val controller = UIActivityViewController(listOf(text), null)
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null)
    }
}

actual fun deviceCurrencyCode(): String? = platform.Foundation.NSLocale.currentLocale.currencyCode
