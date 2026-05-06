package com.yunhwan.humtune.api;

import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.application.AudioUploadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AudioController {

	private final AudioUploadService audioUploadService;

	public AudioController(AudioUploadService audioUploadService) {
		this.audioUploadService = audioUploadService;
	}

	@PostMapping("/api/audio")
	@ResponseStatus(HttpStatus.CREATED)
	public AudioUploadResponse upload(@RequestParam("file") MultipartFile file) {
		return audioUploadService.upload(file);
	}
}
