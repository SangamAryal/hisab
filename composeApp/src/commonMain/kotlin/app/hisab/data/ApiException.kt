package app.hisab.data

/** An error the server answered with (4xx/5xx); [message] is safe to show. */
class ApiException(val status: Int, message: String) : Exception(message)

/** Couldn't reach the server at all (offline, DNS, timeout). */
class OfflineException(cause: Throwable) : Exception("You're offline", cause)
