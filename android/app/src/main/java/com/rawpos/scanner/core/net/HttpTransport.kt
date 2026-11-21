package com.rawpos.scanner.core.net

/** The one HTTP primitive the client needs. Kept tiny so tests can supply a scripted transport. */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: String,
)

data class HttpResponse(
    val status: Int,
    val body: String,
)

/** A transport failure, as distinct from a refusal by the desktop. */
class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** Where the desktop lives and which device this phone claims to be. */
data class Endpoint(
    val baseUrl: String,
    val deviceId: String,
    val pin: String,
)
