package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudioAnalysisProcessorTest {

	@Mock
	private AnalysisRequestRepository analysisRequestRepository;

	@Mock
	private PythonAudioClient pythonAudioClient;

	private AudioAnalysisProcessor audioAnalysisProcessor;

	@BeforeEach
	void setUp() {
		audioAnalysisProcessor = new AudioAnalysisProcessor(
				analysisRequestRepository,
				pythonAudioClient,
				"build/audio-outputs"
		);
	}

	@Test
	void 분석요청을_PROCESSING으로_변경하고_Python_서비스를_호출한다() throws Exception {
		AudioMeta audioMeta = new AudioMeta("sample.wav", "audio/wav", 5L, "build/audio-uploads/sample.wav");
		setField(audioMeta, "audioId", 1L);
		AnalysisRequest analysisRequest = new AnalysisRequest(audioMeta);
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));

		audioAnalysisProcessor.process(2L);

		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
		assertThat(analysisRequest.getProcessingStartedAt()).isNotNull();
		assertThat(analysisRequest.getErrorMessage()).isNull();
		verify(pythonAudioClient).analyze(1L, "build/audio-uploads/sample.wav", "build/audio-outputs");
	}

	@Test
	void 분석요청이_없으면_아무것도_하지_않는다() {
		when(analysisRequestRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatCode(() -> audioAnalysisProcessor.process(999L))
				.doesNotThrowAnyException();
		verifyNoInteractions(pythonAudioClient);
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
