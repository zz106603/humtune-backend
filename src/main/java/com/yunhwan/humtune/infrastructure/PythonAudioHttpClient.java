package com.yunhwan.humtune.infrastructure;

import com.yunhwan.humtune.application.PythonAudioAnalyzeResponse;
import com.yunhwan.humtune.application.PythonAudioClient;
import com.yunhwan.humtune.infrastructure.PythonAudioClientException.FailureCategory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonAudioHttpClient implements PythonAudioClient {

	private static final Logger log = LoggerFactory.getLogger(PythonAudioHttpClient.class);
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);
	private static final int MAX_ERROR_BODY_BYTES = 4096;

	private final RestClient restClient;
	private final String baseUrl;

	public PythonAudioHttpClient(
			RestClient.Builder restClientBuilder,
			@Value("${audio-service.base-url:http://127.0.0.1:8000}") String baseUrl
	) {
		this.baseUrl = baseUrl;
		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.requestFactory(createHttpRequestFactory())
				.requestInterceptor((request, body, execution) -> {
					log.info(
							"Outgoing Python audio request. method={}, uri={}, contentType={}, contentLengthHeader={}, bodyBytes={}, rawBody={}",
							request.getMethod(),
							request.getURI(),
							request.getHeaders().getContentType(),
							request.getHeaders().getContentLength(),
							body.length,
							new String(body, StandardCharsets.UTF_8)
					);
					return execution.execute(request, body);
				})
				.build();
	}

	@Override
	public PythonAudioAnalyzeResponse analyze(Long audioId, String rawAudioPath, String outputDirectory) {
		PythonAudioAnalyzeRequest request = new PythonAudioAnalyzeRequest(
				String.valueOf(audioId),
				normalizePath(rawAudioPath),
				normalizePath(outputDirectory)
		);
		log.info(
				"Requesting Python audio analysis. baseUrl={}, request={}",
				baseUrl,
				request
		);

		try {
			return restClient.post()
					.uri("/internal/audio/analyze")
					.contentType(MediaType.APPLICATION_JSON)
					.body(request)
					.retrieve()
					.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (httpRequest, response) -> {
						String responseBody = readLimitedErrorBody(response.getBody());
						log.error(
								"Python audio error response. status={}, headers={}, body={}",
								response.getStatusCode(),
								response.getHeaders(),
								responseBody
						);
						throw new PythonAudioClientException(
								FailureCategory.PYTHON_HTTP_ERROR,
								"Python audio service returned " + response.getStatusCode() + ". body=" + responseBody
						);
					})
					.body(PythonAudioAnalyzeResponse.class);
		} catch (RestClientException ex) {
			throw new PythonAudioClientException(
					classifyFailure(ex),
					"Python audio service request failed: " + safeMessage(ex),
					ex
			);
		}
	}

	static FailureCategory classifyFailure(Throwable throwable) {
		if (hasCause(throwable, SocketTimeoutException.class)
				|| hasCauseNameContaining(throwable, "Timeout")
				|| hasCauseNameContaining(throwable, "timed out")) {
			return FailureCategory.PYTHON_TIMEOUT;
		}
		if (hasCause(throwable, ConnectException.class)
				|| hasCause(throwable, UnknownHostException.class)
				|| hasCause(throwable, NoRouteToHostException.class)
				|| hasCauseNameContaining(throwable, "ConnectException")) {
			return FailureCategory.PYTHON_NETWORK_ERROR;
		}
		return FailureCategory.PYTHON_CLIENT_ERROR;
	}

	private static boolean hasCause(Throwable throwable, Class<? extends Throwable> targetType) {
		Throwable current = throwable;
		while (current != null) {
			if (targetType.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static boolean hasCauseNameContaining(Throwable throwable, String text) {
		String normalizedText = text.toLowerCase();
		Throwable current = throwable;
		while (current != null) {
			String className = current.getClass().getSimpleName().toLowerCase();
			String message = current.getMessage();
			String normalizedMessage = message == null ? "" : message.toLowerCase();
			if (className.contains(normalizedText) || normalizedMessage.contains(normalizedText)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private String safeMessage(Throwable throwable) {
		if (throwable.getMessage() == null || throwable.getMessage().isBlank()) {
			return throwable.getClass().getSimpleName();
		}
		return throwable.getMessage();
	}

	private String normalizePath(String path) {
		return path.replace("\\", "/");
	}

	private String readLimitedErrorBody(InputStream inputStream) throws IOException {
		byte[] buffer = inputStream.readNBytes(MAX_ERROR_BODY_BYTES + 1);
		boolean truncated = buffer.length > MAX_ERROR_BODY_BYTES;
		int length = truncated ? MAX_ERROR_BODY_BYTES : buffer.length;
		String body = new String(buffer, 0, length, StandardCharsets.UTF_8);
		if (truncated) {
			return body + "...[truncated]";
		}
		return body;
	}

	private HttpComponentsClientHttpRequestFactory createHttpRequestFactory() {
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
				.build();
		RequestConfig requestConfig = RequestConfig.custom()
				.setResponseTimeout(Timeout.of(RESPONSE_TIMEOUT))
				.build();
		PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setDefaultConnectionConfig(connectionConfig)
				.build();
		HttpClient httpClient = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.setDefaultRequestConfig(requestConfig)
				.build();
		return new HttpComponentsClientHttpRequestFactory(httpClient);
	}
}
