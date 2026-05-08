package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class AudioAnalysisPreparationServiceTest {

	@Mock
	private AnalysisRequestRepository analysisRequestRepository;

	private AudioAnalysisPreparationService audioAnalysisPreparationService;

	@BeforeEach
	void setUp() {
		audioAnalysisPreparationService = new AudioAnalysisPreparationService(
				analysisRequestRepository,
				"build/audio-outputs"
		);
	}

	@Test
	void 분석요청을_PROCESSING으로_변경하고_Python_호출_데이터를_반환한다() throws Exception {
		AudioMeta audioMeta = new AudioMeta("sample.wav", "audio/wav", 5L, "build/audio-uploads/sample.wav");
		setField(audioMeta, "audioId", 1L);
		AnalysisRequest analysisRequest = new AnalysisRequest(audioMeta);
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));

		Optional<PythonAudioAnalysisCommand> command = audioAnalysisPreparationService.markProcessing(2L);

		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
		assertThat(analysisRequest.getProcessingStartedAt()).isNotNull();
		assertThat(analysisRequest.getErrorMessage()).isNull();
		assertThat(command).contains(new PythonAudioAnalysisCommand(
				1L,
				"build/audio-uploads/sample.wav",
				"build/audio-outputs"
		));
	}

	@Test
	void markProcessing은_트랜잭션_메서드다() throws Exception {
		Method markProcessing = AudioAnalysisPreparationService.class.getMethod("markProcessing", Long.class);

		assertThat(markProcessing.isAnnotationPresent(Transactional.class)).isTrue();
	}

	@Test
	void 분석요청을_COMPLETED로_변경한다() {
		AnalysisRequest analysisRequest = new AnalysisRequest(
				new AudioMeta("sample.wav", "audio/wav", 5L, "build/audio-uploads/sample.wav")
		);
		analysisRequest.markProcessing();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));

		audioAnalysisPreparationService.markCompleted(2L);

		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(analysisRequest.getCompletedAt()).isNotNull();
		assertThat(analysisRequest.getErrorMessage()).isNull();
	}

	@Test
	void 분석요청을_FAILED로_변경한다() {
		AnalysisRequest analysisRequest = new AnalysisRequest(
				new AudioMeta("sample.wav", "audio/wav", 5L, "build/audio-uploads/sample.wav")
		);
		analysisRequest.markProcessing();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));

		audioAnalysisPreparationService.markFailed(2L, "Python audio analysis failed: read timed out");

		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysisRequest.getFailedAt()).isNotNull();
		assertThat(analysisRequest.getErrorMessage()).isEqualTo("Python audio analysis failed: read timed out");
	}

	@Test
	void 완료와_실패_변경_메서드는_트랜잭션_메서드다() throws Exception {
		Method markCompleted = AudioAnalysisPreparationService.class.getMethod("markCompleted", Long.class);
		Method markFailed = AudioAnalysisPreparationService.class.getMethod("markFailed", Long.class, String.class);

		assertThat(markCompleted.isAnnotationPresent(Transactional.class)).isTrue();
		assertThat(markFailed.isAnnotationPresent(Transactional.class)).isTrue();
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
