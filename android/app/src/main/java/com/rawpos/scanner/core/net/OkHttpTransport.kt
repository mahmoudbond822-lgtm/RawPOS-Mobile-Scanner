package com.rawpos.scanner.core.net

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The one real [HttpTransport]: OkHttp with the desktop's pin as its only trust anchor.
 *
 * Deliberate choices:
 *
 * - **`retryOnConnectionFailure(false)`** — the client owns the retry policy (same event id, backoff,
 *   15 s budget). Letting OkHttp retry silently would break the event identity guarantees.
 * - **Timeouts from the protocol** — 2 s connect, 4 s read (§5.6), not OkHttp's generous defaults.
 * - **No redirects, no proxy auth, no cookie jar** — this client talks to one host and nothing else.
 */
class OkHttpTransport(
    pin: String,
    connectTimeoutMillis: Long = 2_000,
    readTimeoutMillis: Long = 4_000,
) : HttpTransport {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(SpkiPin.socketFactory(pin), SpkiPin.trustManager(pin))
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .writeTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(request.url)
        for ((name, value) in request.headers) {
            builder.header(name, value)
        }
        if (request.body.isEmpty()) {
            builder.get()
        } else {
            builder.post(request.body.toRequestBody(JSON))
        }
        client.newCall(builder.build()).execute().use { response ->
            HttpResponse(status = response.code, body = response.body?.string().orEmpty())
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
