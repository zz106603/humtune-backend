package com.yunhwan.humtune.api;

import com.yunhwan.humtune.api.dto.AudioAnalysisResultResponse;
import com.yunhwan.humtune.api.dto.AudioStatusResponse;
import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.application.AudioAnalysisResultService;
import com.yunhwan.humtune.application.AudioAnalysisResultService.ResultFile;
import com.yunhwan.humtune.application.AudioStatusService;
import com.yunhwan.humtune.application.AudioUploadService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AudioController {

	private final AudioUploadService audioUploadService;
	private final AudioStatusService audioStatusService;
	private final AudioAnalysisResultService audioAnalysisResultService;

	public AudioController(
			AudioUploadService audioUploadService,
			AudioStatusService audioStatusService,
			AudioAnalysisResultService audioAnalysisResultService
	) {
		this.audioUploadService = audioUploadService;
		this.audioStatusService = audioStatusService;
		this.audioAnalysisResultService = audioAnalysisResultService;
	}

	@PostMapping("/api/audio")
	@ResponseStatus(HttpStatus.CREATED)
	public AudioUploadResponse upload(@RequestParam("file") MultipartFile file) {
		return audioUploadService.upload(file);
	}

	@GetMapping("/api/audio/{audioId}")
	public AudioStatusResponse getStatus(@PathVariable Long audioId) {
		return audioStatusService.getStatus(audioId);
	}

	@GetMapping("/api/audio/{audioId}/result")
	public AudioAnalysisResultResponse getResult(@PathVariable Long audioId) {
		return audioAnalysisResultService.getResult(audioId);
	}

	@GetMapping("/api/audio/{audioId}/files/preview")
	public ResponseEntity<Resource> getPreviewFile(@PathVariable Long audioId) {
		ResultFile file = audioAnalysisResultService.getPreviewFile(audioId);
		return ResponseEntity.ok()
				.contentType(file.mediaType())
				.header(HttpHeaders.CONTENT_DISPOSITION, inlineDisposition(file.filename()))
				.body(file.resource());
	}

	@GetMapping("/api/audio/{audioId}/files/midi")
	public ResponseEntity<Resource> getMidiFile(@PathVariable Long audioId) {
		ResultFile file = audioAnalysisResultService.getMidiFile(audioId);
		return ResponseEntity.ok()
				.contentType(file.mediaType())
				.header(HttpHeaders.CONTENT_DISPOSITION, attachmentDisposition(file.filename()))
				.body(file.resource());
	}

	private String inlineDisposition(String filename) {
		return ContentDisposition.inline()
				.filename(filename, StandardCharsets.UTF_8)
				.build()
				.toString();
	}

	private String attachmentDisposition(String filename) {
		return ContentDisposition.attachment()
				.filename(filename, StandardCharsets.UTF_8)
				.build()
				.toString();
	}
}
