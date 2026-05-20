package com.yunhwan.humtune.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.yunhwan.humtune.application.AiFeedbackClient;
import com.yunhwan.humtune.application.AiFeedbackPrompt;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "ai-feedback.enabled", havingValue = "true")
public class GeminiFeedbackClient implements AiFeedbackClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiFeedbackClient.class);

	private final RestClient restClient;
	private final String apiKey;
	private final String baseUrl;
	private final String model;
	private final String generateContentPath;
	private final Duration connectTimeout;
	private final Duration readTimeout;

	public GeminiFeedbackClient(
			RestClient.Builder restClientBuilder,
			@Value("${gemini.api-key:}") String apiKey,
			@Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
			@Value("${gemini.model:}") String model,
			@Value("${gemini.generate-content-path:/v1beta/models/{model}:generateContent}") String generateContentPath,
			@Value("${gemini.connect-timeout:3s}") Duration connectTimeout,
			@Value("${gemini.read-timeout:10s}") Duration readTimeout
	) {
		this.apiKey = apiKey;
		this.baseUrl = baseUrl;
		this.model = model;
		this.generateContentPath = generateContentPath;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.requestFactory(createHttpRequestFactory())
				.build();
	}

	@PostConstruct
	void logConfigurationStatus() {
		log.info(
				"Gemini feedback client enabled. apiKeyConfigured={}, modelConfigured={}, baseUrl={}, generateContentPath={}, connectTimeout={}, readTimeout={}",
				!isBlank(apiKey),
				!isBlank(model),
				baseUrl,
				generateContentPath,
				connectTimeout,
				readTimeout
		);
	}

	@Override
	public String generateFeedback(AiFeedbackPrompt prompt) {
		boolean apiKeyMissing = isBlank(apiKey);
		boolean modelMissing = isBlank(model);
		if (apiKeyMissing || modelMissing) {
			log.info(
					"Skipping Gemini feedback request. apiKeyConfigured={}, modelConfigured={}",
					!apiKeyMissing,
					!modelMissing
			);
			return null;
		}
		String path = generateContentPath.replace("{model}", model);
		log.info("Requesting Gemini feedback. baseUrl={}, path={}, model={}", baseUrl, path, model);
		GeminiGenerateContentResponse response = restClient.post()
				.uri(uriBuilder -> uriBuilder
						.path(path)
						.queryParam("key", apiKey)
						.build())
				.contentType(MediaType.APPLICATION_JSON)
				.body(GeminiGenerateContentRequest.from(prompt))
				.retrieve()
				.body(GeminiGenerateContentResponse.class);
		return extractText(response);
	}

	private String extractText(GeminiGenerateContentResponse response) {
		if (response == null || response.candidates() == null) {
			return null;
		}
		return response.candidates().stream()
				.filter(candidate -> candidate.content() != null && candidate.content().parts() != null)
				.flatMap(candidate -> candidate.content().parts().stream())
				.map(GeminiPart::text)
				.filter(text -> text != null && !text.isBlank())
				.findFirst()
				.orElse(null);
	}

	private HttpComponentsClientHttpRequestFactory createHttpRequestFactory() {
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(connectTimeout))
				.build();
		RequestConfig requestConfig = RequestConfig.custom()
				.setResponseTimeout(Timeout.of(readTimeout))
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

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private record GeminiGenerateContentRequest(
			@JsonProperty("system_instruction")
			GeminiContent systemInstruction,
			List<GeminiContent> contents
	) {
		static GeminiGenerateContentRequest from(AiFeedbackPrompt prompt) {
			return new GeminiGenerateContentRequest(
					GeminiContent.system(prompt.systemPrompt()),
					List.of(GeminiContent.user(prompt.userPrompt()))
			);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record GeminiContent(
			String role,
			List<GeminiPart> parts
	) {
		static GeminiContent system(String text) {
			return new GeminiContent(null, List.of(new GeminiPart(text)));
		}

		static GeminiContent user(String text) {
			return new GeminiContent("user", List.of(new GeminiPart(text)));
		}
	}

	private record GeminiPart(String text) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GeminiGenerateContentResponse(List<GeminiCandidate> candidates) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record GeminiCandidate(GeminiContent content) {
	}
}
