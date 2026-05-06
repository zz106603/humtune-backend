package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import com.yunhwan.humtune.domain.audio.AudioMetaRepository;
import com.yunhwan.humtune.infrastructure.LocalAudioStorage;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AudioUploadServiceTest {

	@Mock
	private AudioMetaRepository audioMetaRepository;

	@Mock
	private AnalysisRequestRepository analysisRequestRepository;

	@Mock
	private LocalAudioStorage localAudioStorage;

	private AudioUploadService audioUploadService;

	@BeforeEach
	void setUp() {
		audioUploadService = new AudioUploadService(audioMetaRepository, analysisRequestRepository, localAudioStorage);
	}

	@Test
	void 오디오_업로드시_메타데이터와_대기중인_분석요청을_생성한다() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "sample.wav", "audio/wav", "audio".getBytes());
		when(localAudioStorage.store(file)).thenReturn("build/audio-uploads/sample.wav");
		when(audioMetaRepository.save(any())).thenAnswer(invocation -> {
			AudioMeta audioMeta = invocation.getArgument(0);
			setField(audioMeta, "audioId", 1L);
			return audioMeta;
		});
		when(analysisRequestRepository.save(any())).thenAnswer(invocation -> {
			AnalysisRequest analysisRequest = invocation.getArgument(0);
			setField(analysisRequest, "id", 2L);
			return analysisRequest;
		});

		var response = audioUploadService.upload(file);

		assertThat(response.audioId()).isEqualTo(1L);
		assertThat(response.analysisId()).isEqualTo(2L);
		assertThat(response.status()).isEqualTo(AnalysisStatus.PENDING);
		verify(localAudioStorage).store(file);
		verify(audioMetaRepository).save(any(AudioMeta.class));
		verify(analysisRequestRepository).save(any(AnalysisRequest.class));
	}

	@Test
	void 빈_파일_업로드시_예외가_발생한다() {
		MockMultipartFile file = new MockMultipartFile("file", "sample.wav", "audio/wav", new byte[0]);

		assertThatThrownBy(() -> audioUploadService.upload(file))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400 BAD_REQUEST");
		verify(localAudioStorage, never()).store(any());
	}

	@Test
	void audio가_아닌_content_type이면_예외가_발생한다() {
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "text".getBytes());

		assertThatThrownBy(() -> audioUploadService.upload(file))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("400 BAD_REQUEST");
		verify(localAudioStorage, never()).store(any());
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
