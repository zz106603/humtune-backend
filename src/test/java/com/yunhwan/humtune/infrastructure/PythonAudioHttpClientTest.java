package com.yunhwan.humtune.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yunhwan.humtune.infrastructure.PythonAudioClientException.FailureCategory;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class PythonAudioHttpClientTest {

	@Test
	void Python_오디오_서비스에_HTTP1_요청으로_JSON_body를_전송한다() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		AtomicReference<String> method = new AtomicReference<>();
		AtomicReference<String> path = new AtomicReference<>();
		AtomicReference<String> contentType = new AtomicReference<>();
		AtomicReference<String> contentLength = new AtomicReference<>();
		AtomicReference<String> transferEncoding = new AtomicReference<>();
		AtomicReference<String> connection = new AtomicReference<>();
		AtomicReference<String> upgrade = new AtomicReference<>();
		AtomicReference<String> userAgent = new AtomicReference<>();
		AtomicReference<byte[]> bodyBytes = new AtomicReference<>(new byte[0]);
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/audio/analyze", exchange -> {
			method.set(exchange.getRequestMethod());
			path.set(exchange.getRequestURI().getPath());
			contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			contentLength.set(exchange.getRequestHeaders().getFirst("Content-Length"));
			transferEncoding.set(exchange.getRequestHeaders().getFirst("Transfer-encoding"));
			connection.set(exchange.getRequestHeaders().getFirst("Connection"));
			upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
			userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
			bodyBytes.set(exchange.getRequestBody().readAllBytes());
			byte[] response = """
					{
					  "status": "COMPLETED",
					  "detectedScale": "C_MAJOR",
					  "keyConfidence": 0.9,
					  "originalNotes": [{"pitch": 60}],
					  "adjustedNotes": [{"pitch": 62}],
					  "chords": [{"name": "C"}],
					  "midiPath": "storage/midi/sample.mid",
					  "processingTimeMs": 123
					}
					""".getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			PythonAudioHttpClient client = new PythonAudioHttpClient(RestClient.builder(), baseUrl);

			var response = client.analyze(9L, "storage\\raw\\test.m4a", "storage/midi");

			String rawBody = new String(bodyBytes.get(), StandardCharsets.UTF_8);
			System.out.printf(
					"Captured Python request method=%s path=%s contentType=%s contentLength=%s body=%s%n",
					method.get(),
					path.get(),
					contentType.get(),
					contentLength.get(),
					rawBody
			);
			JsonNode json = objectMapper.readTree(rawBody);
			assertThat(method.get()).isEqualTo("POST");
			assertThat(path.get()).isEqualTo("/internal/audio/analyze");
			assertThat(contentType.get()).startsWith("application/json");
			assertThat(bodyBytes.get()).isNotEmpty();
			assertThat(contentLength.get()).isEqualTo(String.valueOf(bodyBytes.get().length));
			assertThat(transferEncoding.get()).isNull();
			assertThat(connection.get() == null || !connection.get().contains("Upgrade")).isTrue();
			assertThat(connection.get() == null || !connection.get().contains("HTTP2-Settings")).isTrue();
			assertThat(upgrade.get()).isNull();
			assertThat(userAgent.get()).doesNotStartWith("Java-http-client/");
			assertThat(rawBody).contains("audioId", "rawAudioPath", "outputDirectory");
			assertThat(json.get("audioId").isTextual()).isTrue();
			assertThat(json.get("audioId").asText()).isEqualTo("9");
			assertThat(json.get("rawAudioPath").asText()).isEqualTo("storage/raw/test.m4a");
			assertThat(json.get("outputDirectory").asText()).isEqualTo("storage/midi");
			assertThat(response.status()).isEqualTo("COMPLETED");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void Python_오디오_서비스_오류는_전용_예외로_전파한다() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/audio/analyze", exchange -> {
			byte[] response = "invalid request".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(500, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			PythonAudioHttpClient client = new PythonAudioHttpClient(RestClient.builder(), baseUrl);

			assertThatExceptionOfType(PythonAudioClientException.class)
					.isThrownBy(() -> client.analyze(9L, "storage/raw/test.m4a", "storage/midi"))
					.satisfies(ex -> assertThat(ex.getFailureCategory()).isEqualTo(FailureCategory.PYTHON_HTTP_ERROR))
					.withMessageContaining("Python audio service returned");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void Python_오디오_서비스_오류_body는_제한된_길이로_전파한다() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/internal/audio/analyze", exchange -> {
			byte[] response = "x".repeat(5000).getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(500, response.length);
			exchange.getResponseBody().write(response);
			exchange.close();
		});
		server.start();

		try {
			String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			PythonAudioHttpClient client = new PythonAudioHttpClient(RestClient.builder(), baseUrl);

			assertThatThrownBy(() -> client.analyze(9L, "storage/raw/test.m4a", "storage/midi"))
					.isInstanceOf(PythonAudioClientException.class)
					.hasMessageContaining("...[truncated]")
					.hasMessageNotContaining("x".repeat(5000));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void timeout_오류는_TIMEOUT으로_분류한다() {
		RestClientException exception = new RestClientException(
				"request failed",
				new SocketTimeoutException("read timed out")
		);

		assertThat(PythonAudioHttpClient.classifyFailure(exception))
				.isEqualTo(FailureCategory.PYTHON_TIMEOUT);
	}

	@Test
	void timeout_메시지는_대소문자와_무관하게_TIMEOUT으로_분류한다() {
		RestClientException exception = new RestClientException("Read timed out");

		assertThat(PythonAudioHttpClient.classifyFailure(exception))
				.isEqualTo(FailureCategory.PYTHON_TIMEOUT);
	}

	@Test
	void connect_오류는_NETWORK_ERROR로_분류한다() {
		RestClientException exception = new RestClientException(
				"request failed",
				new ConnectException("Connection refused")
		);

		assertThat(PythonAudioHttpClient.classifyFailure(exception))
				.isEqualTo(FailureCategory.PYTHON_NETWORK_ERROR);
	}

	@Test
	void connect_메시지는_대소문자와_무관하게_NETWORK_ERROR로_분류한다() {
		RestClientException exception = new RestClientException("ConnectException: Connection refused");

		assertThat(PythonAudioHttpClient.classifyFailure(exception))
				.isEqualTo(FailureCategory.PYTHON_NETWORK_ERROR);
	}

	@Test
	void Python_오디오_서비스_네트워크_오류는_전용_예외로_전파한다() throws Exception {
		int unusedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			unusedPort = socket.getLocalPort();
		}
		PythonAudioHttpClient client = new PythonAudioHttpClient(
				RestClient.builder(),
				"http://127.0.0.1:" + unusedPort
		);

		assertThatExceptionOfType(PythonAudioClientException.class)
				.isThrownBy(() -> client.analyze(9L, "storage/raw/test.m4a", "storage/midi"))
				.satisfies(ex -> assertThat(ex.getFailureCategory()).isEqualTo(FailureCategory.PYTHON_NETWORK_ERROR))
				.withMessageContaining("Python audio service request failed");
	}
}
