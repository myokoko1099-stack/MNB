package com.mnb.browser.translation.gemini

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * [GeminiHttpClient] on top of the platform's HttpURLConnection (the project has no HTTP library
 * and none is added).
 *
 * - HTTPS only: any other scheme returns [GeminiHttpFailure.INSECURE_URL] before a connection opens.
 * - Redirects are not followed, so request headers are never forwarded to another address.
 * - The response body is read up to [maxResponseBytes]; a larger one returns
 *   [GeminiHttpFailure.TOO_LARGE].
 * - Exceptions become [GeminiHttpFailure] codes. No exception message or cause is kept.
 *
 * Blocking: call it off the main thread.
 */
class HttpUrlConnectionGeminiClient(
    private val connectTimeoutMillis: Int = GeminiConfig.DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = GeminiConfig.DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxResponseBytes: Int = GeminiConfig.MAX_RESPONSE_BYTES,
) : GeminiHttpClient {

    override fun post(request: GeminiHttpRequest): GeminiHttpResult {
        val url = try {
            URL(request.url)
        } catch (e: Exception) {
            return GeminiHttpResult.Failure(GeminiHttpFailure.IO)
        }
        if (!url.protocol.equals("https", ignoreCase = true)) {
            return GeminiHttpResult.Failure(GeminiHttpFailure.INSECURE_URL)
        }

        var opened: HttpURLConnection? = null
        // True until the connection is open: a timeout in that phase is a connect timeout, later a read timeout.
        var connecting = true
        return try {
            val connection = url.openConnection() as HttpURLConnection
            opened = connection
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            for ((name, value) in request.headers) {
                connection.setRequestProperty(name, value)
            }

            val bytes = request.body.toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.connect()
            connecting = false
            connection.outputStream.use { it.write(bytes) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = if (stream == null) "" else readLimited(stream)
            if (body == null) {
                GeminiHttpResult.Failure(GeminiHttpFailure.TOO_LARGE)
            } else {
                val retryAfter = if (status in 200..299) {
                    null
                } else {
                    GeminiRetryAfter.parseHeader(connection.getHeaderField("Retry-After"), System.currentTimeMillis())
                }
                GeminiHttpResult.Response(GeminiHttpResponse(status, body, retryAfter))
            }
        } catch (e: SocketTimeoutException) {
            GeminiHttpResult.Failure(if (connecting) GeminiHttpFailure.CONNECT_TIMEOUT else GeminiHttpFailure.TIMEOUT)
        } catch (e: UnknownHostException) {
            GeminiHttpResult.Failure(GeminiHttpFailure.NO_CONNECTION)
        } catch (e: ConnectException) {
            GeminiHttpResult.Failure(GeminiHttpFailure.NO_CONNECTION)
        } catch (e: NoRouteToHostException) {
            GeminiHttpResult.Failure(GeminiHttpFailure.NO_CONNECTION)
        } catch (e: SSLException) {
            GeminiHttpResult.Failure(GeminiHttpFailure.TLS)
        } catch (e: IOException) {
            GeminiHttpResult.Failure(GeminiHttpFailure.IO)
        } catch (e: Exception) {
            GeminiHttpResult.Failure(GeminiHttpFailure.IO)
        } finally {
            try {
                opened?.disconnect()
            } catch (e: Exception) {
                // Nothing to do: the request is over either way.
            }
        }
    }

    /** The body as text, or null when it is longer than [maxResponseBytes]. */
    private fun readLimited(stream: InputStream): String? {
        stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxResponseBytes) return null
                out.write(buffer, 0, read)
            }
            return String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
