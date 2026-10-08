package app.hisab.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createHttpClient(): HttpClient = HttpClient(Darwin)

/** The iOS simulator shares the Mac's network, so localhost works. */
actual val localDevApiUrl: String = "http://127.0.0.1:8090"
