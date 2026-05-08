package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
	private PythonAudioClient pythonAudioClient;

	private AudioAnalysisProcessor audioAnalysisProcessor;

	@BeforeEach
	void setUp() {
		audioAnalysisProcessor = new AudioAnalysisProcessor(
				audioAnalysisPreparationService,
				pythonAudioClient
		);
	}

	@Test
	void PROCESSING_전환_후_Python_서비스를_호출하고_COMPLETED로_변경한다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"build/audio-uploads/sample.wav",
						"build/audio-outputs"
				)));

		audioAnalysisProcessor.process(2L);

		InOrder inOrder = inOrder(audioAnalysisPreparationService, pythonAudioClient);
		inOrder.verify(audioAnalysisPreparationService).markProcessing(2L);
		inOrder.verify(pythonAudioClient).analyze(1L, "build/audio-uploads/sample.wav", "build/audio-outputs");
		inOrder.verify(audioAnalysisPreparationService).markCompleted(2L);
	}

	@Test
	void 분석요청이_없으면_아무것도_하지_않는다() {
		when(audioAnalysisPreparationService.markProcessing(999L)).thenReturn(Optional.empty());

		assertThatCode(() -> audioAnalysisProcessor.process(999L))
				.doesNotThrowAnyException();
		verifyNoInteractions(pythonAudioClient);
	}

	@Test
	void Python_서비스_실패시_FAILED로_변경하고_예외를_전파하지_않는다() {
		when(audioAnalysisPreparationService.markProcessing(2L))
				.thenReturn(Optional.of(new PythonAudioAnalysisCommand(
						1L,
						"build/audio-uploads/sample.wav",
						"build/audio-outputs"
				)));
		doThrow(new RuntimeException("read timed out"))
				.when(pythonAudioClient)
				.analyze(1L, "build/audio-uploads/sample.wav", "build/audio-outputs");

		assertThatCode(() -> audioAnalysisProcessor.process(2L))
				.doesNotThrowAnyException();

		verify(audioAnalysisPreparationService, never()).markCompleted(2L);
		verify(audioAnalysisPreparationService).markFailed(eq(2L), contains("read timed out"));
	}

	@Test
	void process는_트랜잭션_메서드가_아니다() throws Exception {
		Method process = AudioAnalysisProcessor.class.getMethod("process", Long.class);

		assertThat(process.isAnnotationPresent(Transactional.class)).isFalse();
	}
}
