package com.yunhwan.humtune.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunhwan.humtune.api.dto.AudioAnalysisResultResponse;
import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisResult;
import com.yunhwan.humtune.domain.analysis.AnalysisResultRepository;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AudioAnalysisResultService {

	private final AnalysisRequestRepository analysisRequestRepository;
	private final AnalysisResultRepository analysisResultRepository;
	private final ObjectMapper objectMapper;
	private final Path outputDirectory;

	public AudioAnalysisResultService(
			AnalysisRequestRepository analysisRequestRepository,
			AnalysisResultRepository analysisResultRepository,
			ObjectMapper objectMapper,
			@Value("${humtune.audio.output-directory:storage/midi}") Path outputDirectory
	) {
		this.analysisRequestRepository = analysisRequestRepository;
		this.analysisResultRepository = analysisResultRepository;
		this.objectMapper = objectMapper;
		this.outputDirectory = outputDirectory;
	}

	@Transactional
	public void completeWithResult(Long analysisRequestId, PythonAudioAnalyzeResponse response) {
		analysisRequestRepository.findById(analysisRequestId)
				.ifPresent(analysisRequest -> {
					if (!"COMPLETED".equals(response.status())) {
						analysisRequest.markFailed(safeErrorMessage(response.errorMessage()));
						return;
					}
					if (hasMissingRequiredResultField(response)) {
						analysisRequest.markFailed("Python audio service returned incomplete result");
						return;
					}
					analysisResultRepository.save(AnalysisResult.builder()
							.analysisRequest(analysisRequest)
							.detectedScale(response.detectedScale())
							.keyConfidence(response.keyConfidence())
							.originalNotesJson(writeJson(response.originalNotes()))
							.adjustedNotesJson(writeJson(response.adjustedNotes()))
							.chordsJson(writeJson(response.chords()))
							.midiPath(toStoredPath(response.midiPath()))
							.processingTimeMs(response.processingTimeMs())
							.build());
					analysisRequest.markCompleted();
				});
	}

	@Transactional(readOnly = true)
	public AudioAnalysisResultResponse getResult(Long audioId) {
		AnalysisRequest analysisRequest = analysisRequestRepository.findByAudioMeta_AudioId(audioId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));
		AudioMeta audioMeta = analysisRequest.getAudioMeta();
		AnalysisStatus status = analysisRequest.getStatus();

		if (status != AnalysisStatus.COMPLETED) {
			return emptyResult(audioMeta.getAudioId(), status, analysisRequest.getErrorMessage());
		}

		return analysisResultRepository.findByAnalysisRequest(analysisRequest)
				.map(result -> new AudioAnalysisResultResponse(
						audioMeta.getAudioId(),
						status,
						result.getDetectedScale(),
						result.getKeyConfidence(),
						readJson(result.getOriginalNotesJson()),
						readJson(result.getAdjustedNotesJson()),
						readJson(result.getChordsJson()),
						result.getMidiPath(),
						result.getProcessingTimeMs(),
						null
				))
				.orElseGet(() -> emptyResult(audioMeta.getAudioId(), status, null));
	}

	private AudioAnalysisResultResponse emptyResult(Long audioId, AnalysisStatus status, String errorMessage) {
		return new AudioAnalysisResultResponse(
				audioId,
				status,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				errorMessage
		);
	}

	private String writeJson(JsonNode jsonNode) {
		try {
			return objectMapper.writeValueAsString(jsonNode);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize analysis result", ex);
		}
	}

	private JsonNode readJson(String json) {
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to read analysis result", ex);
		}
	}

	private boolean hasMissingRequiredResultField(PythonAudioAnalyzeResponse response) {
		return isBlank(response.detectedScale())
				|| response.originalNotes() == null
				|| response.adjustedNotes() == null
				|| response.chords() == null
				|| isBlank(response.midiPath());
	}

	private String toStoredPath(String path) {
		Path midiPath = Path.of(path).normalize();
		if (!midiPath.isAbsolute()) {
			return normalizeSeparators(midiPath);
		}
		Path absoluteOutputDirectory = outputDirectory.toAbsolutePath().normalize();
		if (midiPath.startsWith(absoluteOutputDirectory)) {
			return normalizeSeparators(outputDirectory.normalize().resolve(absoluteOutputDirectory.relativize(midiPath)));
		}
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		if (midiPath.startsWith(workingDirectory)) {
			return normalizeSeparators(workingDirectory.relativize(midiPath));
		}
		return normalizeSeparators(outputDirectory.resolve(midiPath.getFileName()).normalize());
	}

	private String normalizeSeparators(Path path) {
		return path.toString().replace("\\", "/");
	}

	private String safeErrorMessage(String errorMessage) {
		if (isBlank(errorMessage)) {
			return "Python audio service failed";
		}
		return errorMessage;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
