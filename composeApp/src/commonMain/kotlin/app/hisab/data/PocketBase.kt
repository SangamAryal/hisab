package app.hisab.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val HisabJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Small typed client for the PocketBase REST API. */
class PocketBase(
    baseUrl: String,
    engine: HttpClient = HttpClient(),
) {
    val baseUrl = baseUrl.trimEnd('/')

    /** Current auth token; the repository keeps it fresh. */
    var token: String? = null

    val http: HttpClient = engine.config {
        expectSuccess = false
        install(ContentNegotiation) { json(HisabJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }

    suspend fun send(
        method: HttpMethod,
        path: String,
        body: JsonElement? = null,
        auth: Boolean = true,
        block: HttpRequestBuilder.() -> Unit = {},
    ): HttpResponse {
        val response = try {
            http.request(baseUrl + path) {
                this.method = method
                if (auth) token?.let { header("Authorization", it) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                block()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw OfflineException(e)
        }
        if (!response.status.isSuccess()) throw ApiException(response.status.value, errorMessage(response))
        return response
    }

    suspend inline fun <reified T> get(path: String, noinline block: HttpRequestBuilder.() -> Unit = {}): T =
        send(HttpMethod.Get, path, block = block).body()

    suspend inline fun <reified T> post(path: String, body: JsonElement? = null, auth: Boolean = true): T =
        send(HttpMethod.Post, path, body ?: JsonObject(emptyMap()), auth).body()

    suspend inline fun <reified T> patch(path: String, body: JsonElement): T =
        send(HttpMethod.Patch, path, body).body()

    suspend fun delete(path: String) {
        send(HttpMethod.Delete, path)
    }

    /** Lists every record of [collection] matching [filter] (follows pagination). */
    suspend inline fun <reified T> listAll(collection: String, filter: String, sort: String = ""): List<T> {
        val out = mutableListOf<T>()
        var page = 1
        while (true) {
            val p: Page<T> = get("/api/collections/$collection/records") {
                parameter("filter", filter)
                if (sort.isNotEmpty()) parameter("sort", sort)
                parameter("perPage", 500)
                parameter("page", page)
                parameter("skipTotal", 1)
            }
            out += p.items
            if (p.items.size < 500) return out
            page++
        }
    }

    private suspend fun errorMessage(response: HttpResponse): String {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val obj = runCatching { HisabJson.parseToJsonElement(text).jsonObject }.getOrNull()
        // Prefer a field-level message ("splits must add up..."), then the top-level one.
        val fieldMessage = (obj?.get("data") as? JsonObject)?.values
            ?.firstNotNullOfOrNull { (it as? JsonObject)?.get("message")?.jsonPrimitive?.content }
        val message = obj?.get("message")?.jsonPrimitive?.content
        return fieldMessage ?: message ?: "Something went wrong (${response.status.value})"
    }

    companion object {
        /** Quotes a value for a PocketBase filter string. */
        fun q(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
}
