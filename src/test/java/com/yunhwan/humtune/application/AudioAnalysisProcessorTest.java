package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.humtune.infrastructure.PythonAudioClientException;
import com.yunhwan.humtune.infrastructure.PythonAudioClientException.FailureCategory;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AudioAnalysisProcessorTest {

	@Mock
	private AudioAnalysisPreparationService audioAnalysisPreparationService;

	@Mock
	private AudioAnalysisResultService audioAnalysisResultService;

	@Mock
	private PythonAudioClient pythonAudioClient;

	private AudioAnalysisProcessor audioAnalysisProcessor;

	@BeforeEach
	void setUp() {
		audioAnalysisProcessor = new AudioAnalysisProcessor(
				audioAnalysisPreparationService,
				audioAnalysisResultService,
				pythonAudioClient
		);
	}

	@Test
	void PROCESSING_전환_후_Python_서비스를_호출하고_분석결과를_저장한다() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		PythonAudioAnalyzeResponse response = new PythonAudioAnalyzeResponse(
				"COMPLETED",
				"C_MAJOR",
				0.9,
				objectMapper.readTree("[{\"pitch\":60}]"),
				objectMapper.readTree("[{\"pitch\":62}]"),
				objectMapper.readTree("[\"C\"]"),
				"storage/midi/sample.mid",
				null,
				123L,
				null
		);
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		when(pythonAudioClient.analyze(1L, "storage/raw/sample.wav", "storage/midi"))
				.thenReturn(response);

		audioAnalysisProcessor.process(2L);

		InOrder inOrder = inOrder(audioAnalysisPreparationService, pythonAudioClient, audioAnalysisResultService);
		inOrder.verify(audioAnalysisPreparationService).markProcessing(2L);
		inOrder.verify(pythonAudioClient).analyze(1L, "storage/raw/sample.wav", "storage/midi");
		inOrder.verify(audioAnalysisResultService).completeWithResult(2L, response);
	}

	@Test
	void 분석요청이_없으면_아무것도_하지_않는다() {
		when(audioAnalysisPreparationService.markProcessing(999L)).thenReturn(Optional.empty());

		assertThatCode(() -> audioAnalysisProcessor.process(999L))
				.doesNotThrowAnyException();
		verifyNoInteractions(pythonAudioClient);
		verifyNoInteractions(audioAnalysisResultService);
	}

	@Test
	void Python_HTTP_오류시_짧은_메시지로_FAILED_처리하고_예외를_전파하지_않는다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		doThrow(new PythonAudioClientException(FailureCategory.PYTHON_HTTP_ERROR, "Python audio service returned 500"))
				.when(pythonAudioClient)
				.analyze(1L, "storage/raw/sample.wav", "storage/midi");

		assertThatCode(() -> audioAnalysisProcessor.process(2L))
				.doesNotThrowAnyException();

		verify(audioAnalysisResultService, never()).completeWithResult(eq(2L), org.mockito.ArgumentMatchers.any());
		verify(audioAnalysisPreparationService).markFailed(eq(2L), eq("Python audio service returned an error"));
	}

	@Test
	void Python_timeout_오류시_짧은_메시지로_FAILED_처리한다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		doThrow(new PythonAudioClientException(FailureCategory.PYTHON_TIMEOUT, "read timed out"))
				.when(pythonAudioClient)
				.analyze(1L, "storage/raw/sample.wav", "storage/midi");

		audioAnalysisProcessor.process(2L);

		verify(audioAnalysisPreparationService).markFailed(eq(2L), eq("Python audio service timed out"));
	}

	@Test
	void Python_network_오류시_짧은_메시지로_FAILED_처리한다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		doThrow(new PythonAudioClientException(FailureCategory.PYTHON_NETWORK_ERROR, "connection refused"))
				.when(pythonAudioClient)
				.analyze(1L, "storage/raw/sample.wav", "storage/midi");

		audioAnalysisProcessor.process(2L);

		verify(audioAnalysisPreparationService).markFailed(eq(2L), eq("Python audio service unavailable"));
	}

	@Test
	void Python_unknown_client_오류시_짧은_메시지로_FAILED_처리한다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		doThrow(new PythonAudioClientException(FailureCategory.PYTHON_CLIENT_ERROR, "unexpected client failure"))
				.when(pythonAudioClient)
				.analyze(1L, "storage/raw/sample.wav", "storage/midi");

		audioAnalysisProcessor.process(2L);

		verify(audioAnalysisPreparationService).markFailed(eq(2L), eq("Python audio service failed"));
	}

	@Test
	void 내부_오류시_짧은_fallback_메시지로_FAILED_처리한다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"storage/raw/sample.wav",
						"storage/midi"
				)));
		doThrow(new RuntimeException())
				.when(pythonAudioClient)
				.analyze(1L, "storage/raw/sample.wav", "storage/midi");

		audioAnalysisProcessor.process(2L);

		verify(audioAnalysisPreparationService).markFailed(eq(2L), eq("Audio analysis failed"));
	}

	@Test
	void process는_트랜잭션_메서드가_아니다() throws Exception {
		Method process = AudioAnalysisProcessor.class.getMethod("process", Long.class);

		assertThat(process.isAnnotationPresent(Transactional.class)).isFalse();
	}
}
