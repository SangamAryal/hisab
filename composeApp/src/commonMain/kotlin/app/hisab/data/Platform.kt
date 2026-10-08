package app.hisab.data

import io.ktor.client.HttpClient

/** Platform HTTP engine (OkHttp on Android, Darwin on iOS). */
expect fun createHttpClient(): HttpClient

/**
 * Where the app talks to when no server URL was baked in at build time
 * (see `hisab.apiUrl` in gradle.properties).
 */
expect val localDevApiUrl: String
