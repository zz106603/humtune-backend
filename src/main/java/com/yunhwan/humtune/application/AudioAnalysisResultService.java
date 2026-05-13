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
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
							.previewAudioPath(toStoredPathOrNull(response.previewAudioPath()))
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
						result.getPreviewAudioPath(),
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
				null,
				errorMessage
		);
	}

	@Transactional(readOnly = true)
	public ResultFile getMidiFile(Long audioId) {
		AnalysisResult result = completedResult(audioId);
		return resolveResultFile(result.getMidiPath(), MediaType.APPLICATION_OCTET_STREAM, "MIDI file not found");
	}

	@Transactional(readOnly = true)
	public ResultFile getPreviewFile(Long audioId) {
		AnalysisResult result = completedResult(audioId);
		if (isBlank(result.getPreviewAudioPath())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview audio file not found");
		}
		return resolveResultFile(result.getPreviewAudioPath(), MediaType.parseMediaType("audio/wav"), "Preview audio file not found");
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
		Path resultPath = Path.of(path).normalize();
		if (!resultPath.isAbsolute()) {
			return normalizeSeparators(resultPath);
		}
		Path absoluteOutputDirectory = outputDirectory.toAbsolutePath().normalize();
		if (resultPath.startsWith(absoluteOutputDirectory)) {
			return normalizeSeparators(outputDirectory.normalize().resolve(absoluteOutputDirectory.relativize(resultPath)));
		}
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		if (resultPath.startsWith(workingDirectory)) {
			return normalizeSeparators(workingDirectory.relativize(resultPath));
		}
		return normalizeSeparators(outputDirectory.resolve(resultPath.getFileName()).normalize());
	}

	private String toStoredPathOrNull(String path) {
		if (isBlank(path)) {
			return null;
		}
		return toStoredPath(path);
	}

	private AnalysisResult completedResult(Long audioId) {
		AnalysisRequest analysisRequest = analysisRequestRepository.findByAudioMeta_AudioId(audioId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));
		if (analysisRequest.getStatus() != AnalysisStatus.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis result not found");
		}
		return analysisResultRepository.findByAnalysisRequest(analysisRequest)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis result not found"));
	}

	private ResultFile resolveResultFile(String storedPath, MediaType mediaType, String missingMessage) {
		Path resolvedPath = resolveInsideOutputDirectory(storedPath);
		if (!Files.isRegularFile(resolvedPath)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, missingMessage);
		}
		return new ResultFile(new PathResource(resolvedPath), mediaType, resolvedPath.getFileName().toString());
	}

	private Path resolveInsideOutputDirectory(String storedPath) {
		Path outputRoot = outputDirectory.toAbsolutePath().normalize();
		Path path = Path.of(storedPath).normalize();
		Path resolvedPath;
		if (path.isAbsolute()) {
			resolvedPath = path.toAbsolutePath().normalize();
		} else {
			Path relativeToWorkingDirectory = path.toAbsolutePath().normalize();
			if (relativeToWorkingDirectory.startsWith(outputRoot)) {
				resolvedPath = relativeToWorkingDirectory;
			} else {
				resolvedPath = outputRoot.resolve(path).normalize();
			}
		}
		if (!resolvedPath.startsWith(outputRoot)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Result file not found");
		}
		return resolvedPath;
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

	public record ResultFile(Resource resource, MediaType mediaType, String filename) {
	}
}
