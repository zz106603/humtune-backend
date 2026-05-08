package com.yunhwan.humtune.infrastructure;

import com.yunhwan.humtune.application.PythonAudioAnalyzeResponse;
import com.yunhwan.humtune.application.PythonAudioClient;
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

@Component
public class PythonAudioHttpClient implements PythonAudioClient {

	private static final Logger log = LoggerFactory.getLogger(PythonAudioHttpClient.class);
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(30);

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

		return restClient.post()
				.uri("/internal/audio/analyze")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (httpRequest, response) -> {
					String responseBody = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
					log.error(
							"Python audio error response. status={}, headers={}, body={}",
							response.getStatusCode(),
							response.getHeaders(),
							responseBody
					);
					throw new PythonAudioClientException("Python audio service returned "
							+ response.getStatusCode()
							+ ". body="
							+ responseBody);
				})
				.body(PythonAudioAnalyzeResponse.class);
	}

	private String normalizePath(String path) {
		return path.replace("\\", "/");
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
