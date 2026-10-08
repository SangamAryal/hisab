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

/** A currency formatter uses the phone's current locale, so its code is the local currency. */
actual fun deviceCurrencyCode(): String? =
    platform.Foundation.NSNumberFormatter().apply {
        numberStyle = platform.Foundation.NSNumberFormatterCurrencyStyle
    }.currencyCode
