package app.hisab.data

/** An error the server answered with (4xx/5xx); [message] is safe to show. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Couldn't reach the server at all. The phone may be offline, or the server
 * may be down or misconfigured; from here the two look the same.
 */
class OfflineException(cause: Throwable) : Exception("Can't reach the Hisab server", cause)
