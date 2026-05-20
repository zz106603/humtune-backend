package com.yunhwan.humtune.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yunhwan.humtune.application.AiFeedbackPrompt;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GeminiFeedbackClientTest {

	@Test
	void Gemini_generateContent_경로와_API_key를_사용하고_응답_text를_반환한다() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		AtomicReference<String> path = new AtomicReference<>();
		AtomicReference<String> query = new AtomicReference<>();
		AtomicReference<byte[]> bodyBytes = new AtomicReference<>(new byte[0]);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1beta/models/gemini-test:generateContent", exchange -> {
			path.set(exchange.getRequestURI().getPath());
			query.set(exchange.getRequestURI().getRawQuery());
			bodyBytes.set(exchange.getRequestBody().readAllBytes());
			byte[] response = """
					{
					  "candidates": [
					    {
					      "content": {
					        "parts": [
					          {"text": "리듬이 안정적으로 분석되었습니다."}
					        ]
					      }
					    }
					  ]
					}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			GeminiFeedbackClient client = new GeminiFeedbackClient(
					RestClient.builder(),
					"test-api-key",
					"http://127.0.0.1:" + server.getAddress().getPort(),
					"gemini-test",
					"/v1beta/models/{model}:generateContent",
					Duration.ofSeconds(3),
					Duration.ofSeconds(10)
			);

			String feedback = client.generateFeedback(new AiFeedbackPrompt("system", "user"));

			JsonNode request = objectMapper.readTree(bodyBytes.get());
			assertThat(path.get()).isEqualTo("/v1beta/models/gemini-test:generateContent");
			assertThat(query.get()).isEqualTo("key=test-api-key");
			assertThat(request.get("systemInstruction").get("parts").get(0).get("text").asText()).isEqualTo("system");
			assertThat(request.get("contents").get(0).get("role").asText()).isEqualTo("user");
			assertThat(request.get("contents").get(0).get("parts").get(0).get("text").asText()).isEqualTo("user");
			assertThat(feedback).isEqualTo("리듬이 안정적으로 분석되었습니다.");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void API_key나_model이_없으면_요청하지_않고_null을_반환한다() {
		GeminiFeedbackClient client = new GeminiFeedbackClient(
				RestClient.builder(),
				"",
				"http://127.0.0.1:1",
				"",
				"/v1beta/models/{model}:generateContent",
				Duration.ofSeconds(3),
				Duration.ofSeconds(10)
		);

		assertThat(client.generateFeedback(new AiFeedbackPrompt("system", "user"))).isNull();
	}
}
