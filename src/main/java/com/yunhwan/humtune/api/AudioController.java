package com.yunhwan.humtune.api;

import com.yunhwan.humtune.api.dto.AudioAnalysisResultResponse;
import com.yunhwan.humtune.api.dto.AudioStatusResponse;
import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.application.AudioAnalysisResultService;
import com.yunhwan.humtune.application.AudioStatusService;
import com.yunhwan.humtune.application.AudioUploadService;
import org.springframework.http.HttpStatus;
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
}
