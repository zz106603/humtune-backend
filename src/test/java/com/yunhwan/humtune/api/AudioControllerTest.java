package com.yunhwan.humtune.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.humtune.api.dto.AudioAnalysisResultResponse;
import com.yunhwan.humtune.api.dto.AudioStatusResponse;
import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.application.AudioAnalysisResultService;
import com.yunhwan.humtune.application.AudioAnalysisResultService.ResultFile;
import com.yunhwan.humtune.application.AudioStatusService;
import com.yunhwan.humtune.application.AudioUploadService;
import com.yunhwan.humtune.common.CorsConfig;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.PathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AudioController.class)
@Import(CorsConfig.class)
@TestPropertySource(properties = "humtune.cors.allowed-origins=http://localhost:5173,https://frontend.example.com")
class AudioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AudioUploadService audioUploadService;

	@MockitoBean
	private AudioStatusService audioStatusService;

	@MockitoBean
	private AudioAnalysisResultService audioAnalysisResultService;

	@MockitoBean
	private JpaMetamodelMappingContext jpaMetamodelMappingContext;

	@Test
	void 오디오_업로드에_성공한다() throws Exception {
		when(audioUploadService.upload(any())).thenReturn(new AudioUploadResponse(1L, 2L, AnalysisStatus.PENDING));
		MockMultipartFile file = new MockMultipartFile("file", "sample.wav", "audio/wav", "audio".getBytes());

		mockMvc.perform(multipart("/api/audio").file(file))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.audioId").value(1))
				.andExpect(jsonPath("$.analysisId").value(2))
				.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void 잘못된_업로드시_400을_반환한다() throws Exception {
		when(audioUploadService.upload(any()))
				.thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content type must be audio/*"));
		MockMultipartFile file = new MockMultipartFile("file", "sample.txt", "text/plain", "text".getBytes());

		mockMvc.perform(multipart("/api/audio").file(file))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 오디오_상태_조회에_성공한다() throws Exception {
		when(audioStatusService.getStatus(eq(1L))).thenReturn(new AudioStatusResponse(
				1L,
				"sample.wav",
				AnalysisStatus.PENDING,
				Instant.parse("2026-05-07T10:15:30Z"),
				null
		));

		mockMvc.perform(get("/api/audio/{audioId}", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.audioId").value(1))
				.andExpect(jsonPath("$.filename").value("sample.wav"))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.createdAt").value("2026-05-07T10:15:30Z"))
				.andExpect(jsonPath("$.errorMessage").value(nullValue()));
	}

	@Test
	void 실패한_오디오_상태_조회시_errorMessage를_반환한다() throws Exception {
		when(audioStatusService.getStatus(eq(1L))).thenReturn(new AudioStatusResponse(
				1L,
				"sample.wav",
				AnalysisStatus.FAILED,
				Instant.parse("2026-05-07T10:15:30Z"),
				"analysis failed"
		));

		mockMvc.perform(get("/api/audio/{audioId}", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.errorMessage").value("analysis failed"));
	}

	@Test
	void 없는_오디오_상태_조회시_404를_반환한다() throws Exception {
		when(audioStatusService.getStatus(eq(999L)))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

		mockMvc.perform(get("/api/audio/{audioId}", 999L))
				.andExpect(status().isNotFound());
	}

	@Test
	void 완료된_오디오_분석결과를_조회한다() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		when(audioAnalysisResultService.getResult(eq(1L))).thenReturn(new AudioAnalysisResultResponse(
				1L,
				AnalysisStatus.COMPLETED,
				"C_MAJOR",
				0.9,
				objectMapper.readTree("[{\"pitch\":60}]"),
				objectMapper.readTree("[{\"pitch\":62}]"),
				objectMapper.readTree("[\"C\"]"),
				"storage/midi/sample.mid",
				"storage/midi/sample.wav",
				123L,
				null
		));

		mockMvc.perform(get("/api/audio/{audioId}/result", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.audioId").value(1))
				.andExpect(jsonPath("$.status").value("COMPLETED"))
				.andExpect(jsonPath("$.detectedScale").value("C_MAJOR"))
				.andExpect(jsonPath("$.keyConfidence").value(0.9))
				.andExpect(jsonPath("$.originalNotes[0].pitch").value(60))
				.andExpect(jsonPath("$.adjustedNotes[0].pitch").value(62))
				.andExpect(jsonPath("$.chords[0]").value("C"))
				.andExpect(jsonPath("$.midiPath").value("storage/midi/sample.mid"))
				.andExpect(jsonPath("$.previewAudioPath").value("storage/midi/sample.wav"))
				.andExpect(jsonPath("$.processingTimeMs").value(123))
				.andExpect(jsonPath("$.errorMessage").value(nullValue()));
	}

	@Test
	void 실패한_오디오_분석결과_조회시_errorMessage를_반환한다() throws Exception {
		when(audioAnalysisResultService.getResult(eq(1L))).thenReturn(new AudioAnalysisResultResponse(
				1L,
				AnalysisStatus.FAILED,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				"Python audio service timed out"
		));

		mockMvc.perform(get("/api/audio/{audioId}/result", 1L))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.detectedScale").value(nullValue()))
				.andExpect(jsonPath("$.errorMessage").value("Python audio service timed out"));
	}

	@Test
	void 없는_오디오_분석결과_조회시_404를_반환한다() throws Exception {
		when(audioAnalysisResultService.getResult(eq(999L)))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));

		mockMvc.perform(get("/api/audio/{audioId}/result", 999L))
				.andExpect(status().isNotFound());
	}

	@Test
	void preview_파일을_audio_wav로_반환한다() throws Exception {
		Path previewFile = Files.createTempFile("preview", ".wav");
		Files.writeString(previewFile, "wav");
		when(audioAnalysisResultService.getPreviewFile(eq(1L))).thenReturn(new ResultFile(
				new PathResource(previewFile),
				MediaType.parseMediaType("audio/wav"),
				"허밍 preview.wav"
		));

		mockMvc.perform(get("/api/audio/{audioId}/files/preview", 1L))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "audio/wav"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("UTF-8")));
	}

	@Test
	void midi_파일을_attachment로_반환한다() throws Exception {
		Path midiFile = Files.createTempFile("sample", ".mid");
		Files.writeString(midiFile, "midi");
		when(audioAnalysisResultService.getMidiFile(eq(1L))).thenReturn(new ResultFile(
				new PathResource(midiFile),
				MediaType.APPLICATION_OCTET_STREAM,
				"허밍 결과.mid"
		));

		mockMvc.perform(get("/api/audio/{audioId}/files/midi", 1L))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "application/octet-stream"))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=")))
				.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("UTF-8")));
	}

	@Test
	void 설정된_localhost_frontend_origin을_CORS에서_허용한다() throws Exception {
		when(audioStatusService.getStatus(eq(1L))).thenReturn(new AudioStatusResponse(
				1L,
				"sample.wav",
				AnalysisStatus.PENDING,
				Instant.parse("2026-05-07T10:15:30Z"),
				null
		));

		mockMvc.perform(get("/api/audio/{audioId}", 1L)
						.header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void 설정으로_추가한_frontend_origin을_CORS에서_허용한다() throws Exception {
		when(audioStatusService.getStatus(eq(1L))).thenReturn(new AudioStatusResponse(
				1L,
				"sample.wav",
				AnalysisStatus.PENDING,
				Instant.parse("2026-05-07T10:15:30Z"),
				null
		));

		mockMvc.perform(get("/api/audio/{audioId}", 1L)
						.header("Origin", "https://frontend.example.com"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"));
	}
}
