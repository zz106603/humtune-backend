package com.yunhwan.humtune.api;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunhwan.humtune.api.dto.AudioStatusResponse;
import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.application.AudioStatusService;
import com.yunhwan.humtune.application.AudioUploadService;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AudioController.class)
class AudioControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AudioUploadService audioUploadService;

	@MockitoBean
	private AudioStatusService audioStatusService;

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
}
