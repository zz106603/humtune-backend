package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisResult;
import com.yunhwan.humtune.domain.analysis.AnalysisResultRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AudioAnalysisResultServiceTest {

	@Mock
	private AnalysisRequestRepository analysisRequestRepository;

	@Mock
	private AnalysisResultRepository analysisResultRepository;

	@Mock
	private AiFeedbackService aiFeedbackService;

	private ObjectMapper objectMapper;
	private AudioAnalysisResultService audioAnalysisResultService;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		audioAnalysisResultService = new AudioAnalysisResultService(
				analysisRequestRepository,
				analysisResultRepository,
				objectMapper,
				aiFeedbackService,
				Path.of("storage/midi")
		);
	}

	@Test
	void Python_응답을_AnalysisResult로_저장하고_COMPLETED로_변경한다() throws Exception {
		AnalysisRequest analysisRequest = processingAnalysisRequest();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));
		PythonAudioAnalyzeResponse response = response();
		when(aiFeedbackService.generateFeedback(response)).thenReturn("리듬이 안정적으로 분석되었습니다.");

		audioAnalysisResultService.completeWithResult(2L, response);

		ArgumentCaptor<AnalysisResult> captor = ArgumentCaptor.forClass(AnalysisResult.class);
		verify(analysisResultRepository).save(captor.capture());
		AnalysisResult result = captor.getValue();
		assertThat(result.getAnalysisRequest()).isSameAs(analysisRequest);
		assertThat(result.getDetectedScale()).isEqualTo("C_MAJOR");
		assertThat(result.getKeyConfidence()).isEqualTo(0.9);
		assertThat(result.getOriginalNotesJson()).contains("\"pitch\":60");
		assertThat(result.getAdjustedNotesJson()).contains("\"pitch\":62");
		assertThat(result.getChordsJson()).contains("\"C\"");
		assertThat(result.getMelodyMetricsJson()).contains("\"scaleToneRatio\":0.8");
		assertThat(result.getFeedbackEvidenceJson()).contains("\"type\":\"scale\"");
		assertThat(result.getMidiPath()).isEqualTo("storage/midi/sample.mid");
		assertThat(result.getPreviewAudioPath()).isEqualTo("storage/midi/sample.wav");
		assertThat(result.getProcessingTimeMs()).isEqualTo(123L);
		assertThat(result.getFeedbackText()).isEqualTo("리듬이 안정적으로 분석되었습니다.");
		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
	}

	@Test
	void 결과_저장_실패시_COMPLETED로_변경하지_않는다() throws Exception {
		AnalysisRequest analysisRequest = processingAnalysisRequest();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));
		when(analysisResultRepository.save(any())).thenThrow(new RuntimeException("db failed"));

		assertThatThrownBy(() -> audioAnalysisResultService.completeWithResult(2L, response()))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("db failed");
		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.PROCESSING);
	}

	@Test
	void Python_FAILED_응답이면_결과를_저장하지_않고_FAILED로_변경한다() throws Exception {
		AnalysisRequest analysisRequest = processingAnalysisRequest();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));
		PythonAudioAnalyzeResponse response = new PythonAudioAnalyzeResponse(
				"FAILED",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"invalid audio file"
		);

		audioAnalysisResultService.completeWithResult(2L, response);

		verify(analysisResultRepository, never()).save(any());
		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysisRequest.getErrorMessage()).isEqualTo("invalid audio file");
	}

	@Test
	void Python_응답의_알_수_없는_필드는_역직렬화에_실패하지_않는다() throws Exception {
		PythonAudioAnalyzeResponse response = objectMapper.readValue("""
				{
				  "status": "COMPLETED",
				  "detectedScale": "C_MAJOR",
				  "keyConfidence": 0.9,
				  "originalNotes": [],
				  "adjustedNotes": [],
				  "chords": [],
				  "melodyMetrics": {"scaleToneRatio": 0.8},
				  "feedbackEvidence": [{"type": "scale"}],
				  "midiPath": "storage/midi/sample.mid",
				  "previewAudioPath": null,
				  "processingTimeMs": 123,
				  "ignoredFutureField": true
				}
				""", PythonAudioAnalyzeResponse.class);

		assertThat(response.melodyMetrics().get("scaleToneRatio").asDouble()).isEqualTo(0.8);
		assertThat(response.feedbackEvidence().get(0).get("type").asText()).isEqualTo("scale");
	}

	@Test
	void COMPLETED_응답에_detectedScale이_없으면_결과를_저장하지_않고_FAILED로_변경한다() throws Exception {
		AnalysisRequest analysisRequest = processingAnalysisRequest();
		when(analysisRequestRepository.findById(2L)).thenReturn(Optional.of(analysisRequest));
		PythonAudioAnalyzeResponse response = new PythonAudioAnalyzeResponse(
				"COMPLETED",
				null,
				0.9,
				objectMapper.readTree("[{\"pitch\":60}]"),
				objectMapper.readTree("[{\"pitch\":62}]"),
				objectMapper.readTree("[\"C\"]"),
				"storage/midi/sample.mid",
				null,
				123L,
				null
		);

		audioAnalysisResultService.completeWithResult(2L, response);

		verify(analysisResultRepository, never()).save(any());
		assertThat(analysisRequest.getStatus()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(analysisRequest.getErrorMessage()).isEqualTo("Python audio service returned incomplete result");
	}

	@Test
	void COMPLETED_결과를_조회한다() throws Exception {
		AnalysisRequest analysisRequest = completedAnalysisRequest();
		AnalysisResult result = AnalysisResult.builder()
				.analysisRequest(analysisRequest)
				.detectedScale("C_MAJOR")
				.keyConfidence(0.9)
				.originalNotesJson("[{\"pitch\":60}]")
				.adjustedNotesJson("[{\"pitch\":62}]")
				.chordsJson("[\"C\"]")
				.melodyMetricsJson("{\"scaleToneRatio\":0.8}")
				.feedbackEvidenceJson("[{\"type\":\"scale\"}]")
				.midiPath("storage/midi/sample.mid")
				.previewAudioPath("storage/midi/sample.wav")
				.processingTimeMs(123L)
				.feedbackText("리듬이 안정적으로 분석되었습니다.")
				.build();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));
		when(analysisResultRepository.findByAnalysisRequest(analysisRequest)).thenReturn(Optional.of(result));

		var response = audioAnalysisResultService.getResult(1L);

		assertThat(response.audioId()).isEqualTo(1L);
		assertThat(response.status()).isEqualTo(AnalysisStatus.COMPLETED);
		assertThat(response.detectedScale()).isEqualTo("C_MAJOR");
		assertThat(response.keyConfidence()).isEqualTo(0.9);
		assertThat(response.originalNotes().get(0).get("pitch").asInt()).isEqualTo(60);
		assertThat(response.adjustedNotes().get(0).get("pitch").asInt()).isEqualTo(62);
		assertThat(response.chords().get(0).asText()).isEqualTo("C");
		assertThat(response.melodyMetrics().get("scaleToneRatio").asDouble()).isEqualTo(0.8);
		assertThat(response.feedbackEvidence().get(0).get("type").asText()).isEqualTo("scale");
		assertThat(response.midiPath()).isEqualTo("storage/midi/sample.mid");
		assertThat(response.previewAudioPath()).isEqualTo("storage/midi/sample.wav");
		assertThat(response.processingTimeMs()).isEqualTo(123L);
		assertThat(response.feedbackText()).isEqualTo("리듬이 안정적으로 분석되었습니다.");
		assertThat(response.errorMessage()).isNull();
	}

	@Test
	void preview_파일을_outputDirectory_안에서_조회한다() throws Exception {
		Path outputDirectory = Files.createTempDirectory("humtune-output");
		Path previewFile = outputDirectory.resolve("sample.wav");
		Files.writeString(previewFile, "wav");
		audioAnalysisResultService = new AudioAnalysisResultService(
				analysisRequestRepository,
				analysisResultRepository,
				objectMapper,
				aiFeedbackService,
				outputDirectory
		);
		AnalysisRequest analysisRequest = completedAnalysisRequest();
		AnalysisResult result = AnalysisResult.builder()
				.analysisRequest(analysisRequest)
				.detectedScale("C_MAJOR")
				.keyConfidence(0.9)
				.originalNotesJson("[]")
				.adjustedNotesJson("[]")
				.chordsJson("[]")
				.midiPath(outputDirectory.resolve("sample.mid").toString())
				.previewAudioPath(outputDirectory.resolve("sample.wav").toString())
				.processingTimeMs(123L)
				.build();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));
		when(analysisResultRepository.findByAnalysisRequest(analysisRequest)).thenReturn(Optional.of(result));

		var file = audioAnalysisResultService.getPreviewFile(1L);

		assertThat(file.mediaType().toString()).isEqualTo("audio/wav");
		assertThat(file.filename()).isEqualTo("sample.wav");
		assertThat(file.resource().exists()).isTrue();
	}

	@Test
	void preview_파일이_없으면_404가_발생한다() throws Exception {
		Path outputDirectory = Files.createTempDirectory("humtune-output");
		audioAnalysisResultService = new AudioAnalysisResultService(
				analysisRequestRepository,
				analysisResultRepository,
				objectMapper,
				aiFeedbackService,
				outputDirectory
		);
		AnalysisRequest analysisRequest = completedAnalysisRequest();
		AnalysisResult result = AnalysisResult.builder()
				.analysisRequest(analysisRequest)
				.detectedScale("C_MAJOR")
				.keyConfidence(0.9)
				.originalNotesJson("[]")
				.adjustedNotesJson("[]")
				.chordsJson("[]")
				.midiPath(outputDirectory.resolve("sample.mid").toString())
				.previewAudioPath(outputDirectory.resolve("missing.wav").toString())
				.processingTimeMs(123L)
				.build();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));
		when(analysisResultRepository.findByAnalysisRequest(analysisRequest)).thenReturn(Optional.of(result));

		assertThatThrownBy(() -> audioAnalysisResultService.getPreviewFile(1L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND")
				.hasMessageContaining("Preview audio file not found");
	}

	@Test
	void result_파일_경로가_outputDirectory_밖이면_404가_발생한다() throws Exception {
		Path outputDirectory = Files.createTempDirectory("humtune-output");
		audioAnalysisResultService = new AudioAnalysisResultService(
				analysisRequestRepository,
				analysisResultRepository,
				objectMapper,
				aiFeedbackService,
				outputDirectory
		);
		AnalysisRequest analysisRequest = completedAnalysisRequest();
		AnalysisResult result = AnalysisResult.builder()
				.analysisRequest(analysisRequest)
				.detectedScale("C_MAJOR")
				.keyConfidence(0.9)
				.originalNotesJson("[]")
				.adjustedNotesJson("[]")
				.chordsJson("[]")
				.midiPath(outputDirectory.resolve("sample.mid").toString())
				.previewAudioPath("../sample.wav")
				.processingTimeMs(123L)
				.build();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));
		when(analysisResultRepository.findByAnalysisRequest(analysisRequest)).thenReturn(Optional.of(result));

		assertThatThrownBy(() -> audioAnalysisResultService.getPreviewFile(1L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND")
				.hasMessageContaining("Result file not found");
	}

	@Test
	void FAILED이면_errorMessage만_반환하고_결과는_조회하지_않는다() throws Exception {
		AnalysisRequest analysisRequest = analysisRequestWithAudioId();
		analysisRequest.markFailed("Python audio service timed out");
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));

		var response = audioAnalysisResultService.getResult(1L);

		assertThat(response.status()).isEqualTo(AnalysisStatus.FAILED);
		assertThat(response.errorMessage()).isEqualTo("Python audio service timed out");
		assertThat(response.detectedScale()).isNull();
		verify(analysisResultRepository, never()).findByAnalysisRequest(any());
	}

	@Test
	void PROCESSING이면_상태만_반환한다() throws Exception {
		AnalysisRequest analysisRequest = processingAnalysisRequest();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));

		var response = audioAnalysisResultService.getResult(1L);

		assertThat(response.status()).isEqualTo(AnalysisStatus.PROCESSING);
		assertThat(response.detectedScale()).isNull();
		assertThat(response.errorMessage()).isNull();
		verify(analysisResultRepository, never()).findByAnalysisRequest(any());
	}

	@Test
	void PENDING이면_상태만_반환한다() throws Exception {
		AnalysisRequest analysisRequest = analysisRequestWithAudioId();
		when(analysisRequestRepository.findByAudioMeta_AudioId(1L)).thenReturn(Optional.of(analysisRequest));

		var response = audioAnalysisResultService.getResult(1L);

		assertThat(response.status()).isEqualTo(AnalysisStatus.PENDING);
		assertThat(response.detectedScale()).isNull();
		assertThat(response.errorMessage()).isNull();
		verify(analysisResultRepository, never()).findByAnalysisRequest(any());
	}

	@Test
	void audioId가_없으면_404가_발생한다() {
		when(analysisRequestRepository.findByAudioMeta_AudioId(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> audioAnalysisResultService.getResult(999L))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("404 NOT_FOUND");
	}

	private PythonAudioAnalyzeResponse response() throws Exception {
		return new PythonAudioAnalyzeResponse(
				"COMPLETED",
				"C_MAJOR",
				0.9,
				objectMapper.readTree("[{\"pitch\":60}]"),
				objectMapper.readTree("[{\"pitch\":62}]"),
				objectMapper.readTree("[\"C\"]"),
				objectMapper.readTree("{\"scaleToneRatio\":0.8}"),
				objectMapper.readTree("[{\"type\":\"scale\"}]"),
				Path.of("storage/midi/sample.mid").toAbsolutePath().normalize().toString(),
				Path.of("storage/midi/sample.wav").toAbsolutePath().normalize().toString(),
				123L,
				null
		);
	}

	private AnalysisRequest processingAnalysisRequest() throws Exception {
		AnalysisRequest analysisRequest = analysisRequestWithAudioId();
		analysisRequest.markProcessing();
		return analysisRequest;
	}

	private AnalysisRequest completedAnalysisRequest() throws Exception {
		AnalysisRequest analysisRequest = analysisRequestWithAudioId();
		analysisRequest.markCompleted();
		return analysisRequest;
	}

	private AnalysisRequest analysisRequestWithAudioId() throws Exception {
		AudioMeta audioMeta = new AudioMeta("sample.wav", "audio/wav", 5L, "storage/raw/sample.wav");
		setField(audioMeta, "audioId", 1L);
		return new AnalysisRequest(audioMeta);
	}

	private void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}
}
