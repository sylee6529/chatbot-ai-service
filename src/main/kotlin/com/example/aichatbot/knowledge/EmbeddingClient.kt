package com.example.aichatbot.knowledge

import com.example.aichatbot.ai.AiProviderException
import com.example.aichatbot.ai.AiProviderTimeoutException
import com.example.aichatbot.ai.AiProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.security.MessageDigest
import java.time.Duration
import kotlin.math.sqrt

interface EmbeddingClient {
    val model: String

    fun embed(text: String): List<Double>
}

@Component
class OpenAiCompatibleEmbeddingClient(
    private val properties: AiProperties,
    restClientBuilder: RestClient.Builder,
) : EmbeddingClient {
    override val model: String
        get() = if (properties.apiKey.isBlank()) FAKE_MODEL else properties.embeddingModel

    private val fakeClient = FakeEmbeddingClient()

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl.trimEnd('/'))
        .requestFactory(requestFactory())
        .build()

    override fun embed(text: String): List<Double> {
        if (properties.apiKey.isBlank()) {
            return fakeClient.embed(text)
        }

        val response = try {
            restClient.post()
                .uri("/embeddings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .body(EmbeddingRequest(input = text, model = properties.embeddingModel))
                .retrieve()
                .body(EmbeddingResponse::class.java)
                ?: throw AiProviderException("Embedding provider returned an empty response")
        } catch (exception: RestClientResponseException) {
            throw AiProviderException("Embedding provider request failed")
        } catch (exception: ResourceAccessException) {
            throw AiProviderTimeoutException("Embedding provider request timed out")
        }

        return response.data.firstOrNull()?.embedding
            ?: throw AiProviderException("Embedding provider returned an empty response")
    }

    private fun requestFactory(): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds))
            setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds))
        }

    private data class EmbeddingRequest(
        val input: String,
        val model: String,
    )

    private data class EmbeddingResponse(
        val data: List<EmbeddingData> = emptyList(),
    )

    private data class EmbeddingData(
        val embedding: List<Double> = emptyList(),
    )

    companion object {
        const val FAKE_MODEL = "fake-local-embedding-v1"
    }
}

class FakeEmbeddingClient : EmbeddingClient {
    override val model: String = OpenAiCompatibleEmbeddingClient.FAKE_MODEL

    override fun embed(text: String): List<Double> {
        val vector = DoubleArray(DIMENSIONS)
        tokenize(text).forEach { token ->
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
            val index = ((digest[0].toInt() and 0xff) + ((digest[1].toInt() and 0xff) shl 8)) % DIMENSIONS
            val sign = if ((digest[2].toInt() and 1) == 0) 1.0 else -1.0
            vector[index] += sign
        }
        val magnitude = sqrt(vector.sumOf { it * it })
        return if (magnitude == 0.0) {
            vector.toList()
        } else {
            vector.map { it / magnitude }
        }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

    companion object {
        private const val DIMENSIONS = 64
    }
}
