package com.novelai.assistant.data.sf

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

fun interface SfTransport {
    suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse
    suspend fun exchange(path: String, headers: Map<String, String>, body: String?, method: String): SfResponse =
        exchange(path, headers, body)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SfNonRedirectClient

internal object SfHttpClientFactory {
    val nonRedirectingClient: HttpClient by lazy { createNonRedirectingClient() }

    fun createNonRedirectingClient(): HttpClient = HttpClient(OkHttp) {
        followRedirects = false
        expectSuccess = false
        engine {
            config {
                followRedirects(false)
                followSslRedirects(false)
                retryOnConnectionFailure(false)
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(25, TimeUnit.SECONDS)
            }
        }
    }
}

object SfHttpPolicy {
    fun retryAfter(value: String?, now: Long = System.currentTimeMillis()): Long? {
        if (value == null) return null
        value.trim().toLongOrNull()?.takeIf { it >= 0 }?.let { return it.coerceAtMost(Long.MAX_VALUE / 1000) * 1000 }
        return runCatching {
            (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli() - now).coerceAtLeast(0)
        }.getOrNull()
    }
}

@Singleton
class KtorSfTransport @Inject constructor(
    @SfNonRedirectClient private val sfClient: HttpClient
) : SfTransport {
    constructor() : this(SfHttpClientFactory.nonRedirectingClient)

    override suspend fun exchange(path: String, headers: Map<String, String>, body: String?): SfResponse =
        exchange(path, headers, body, if (body == null) "GET" else "POST")

    override suspend fun exchange(
        path: String,
        headers: Map<String, String>,
        body: String?,
        method: String
    ): SfResponse = withTimeoutOrNull(25_000) {
        require(!path.contains("..") && !path.contains('#') && !path.contains('\r') && !path.contains('\n'))
        require(method in listOf("GET", "POST", "PUT", "DELETE"))
        val response = sfClient.request("https://api.sfacg.com/$path") {
            this.method = HttpMethod.parse(method.uppercase())
            headers.forEach { (key, value) -> this.headers.append(key, value) }
            if (body != null) setBody(body)
        }
        SfResponse(
            response.status.value,
            response.bodyAsText(),
            response.headers.getAll("Set-Cookie").orEmpty(),
            SfHttpPolicy.retryAfter(response.headers["Retry-After"])
        )
    } ?: throw SfException(SfFailureKind.NETWORK, "菠萝包请求超时，请稍后重试")
}
