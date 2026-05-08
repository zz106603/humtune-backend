package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AudioStatusServiceTest {

	@Mock
	private AnalysisRequestRepository analysisRequestRepository;

	private AudioStatusService audioStatusService;

	@BeforeEach
	void setUp() {
		audioStatusService = new AudioStatusService(analysisRequestRepository);
	}

	@Test
	void audioId로_오디오_메타데이터와_분석_상태를_조회한다() throws Exception {
		AudioMeta audioMeta = new AudioMeta("sample.wav", "audio/wav", 5L, "storage/raw/sample.wav");
		setField(audioMeta, "audioId", 1L);
		setField(audioMeta, "createdAt", Instant.parse("2026-05-07T10:15:30Z"));
		AnalysisRequest analysisRequest = new AnalysisRequest(audioMeta);
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));

		var response = audioStatusService.getStatus(1L);

		assertThat(response.audioId()).isEqualTo(1L);
		assertThat(response.filename()).isEqualTo("sample.wav");
		assertThat(response.status()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-07T10:15:30Z"));
		assertThat(response.errorMessage()).isNull();
	}

	@Test
	void 실패_상태이면_errorMessage를_포함한다() throws Exception {
		AudioMeta audioMeta = new AudioMeta("sample.wav", "audio/wav", 5L, "storage/raw/sample.wav");
		setField(audioMeta, "audioId", 1L);
		setField(audioMeta, "createdAt", Instant.parse("2026-05-07T10:15:30Z"));
		AnalysisRequest analysisRequest = new AnalysisRequest(audioMeta);
		analysisRequest.markFailed("analysis failed");
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));

		var response = audioStatusService.getStatus(1L);

		assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(response.errorMessage()).isEqualTo("analysis failed");
	}

	@Test
	void audioId가_존재하지_않으면_404_예외가_발생한다() {
		when(analysisRequestRepository.findByAudioMeta_AudioId(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> audioStatusService.getStatus(999L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
