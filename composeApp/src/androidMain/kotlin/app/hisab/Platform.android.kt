package app.hisab.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp)

/** The Android emulator reaches the host computer at 10.0.2.2. */
actual val localDevApiUrl: String = "http://10.0.2.2:8090"
