package com.yunhwan.humtune.application;

import com.yunhwan.humtune.api.dto.AudioUploadResponse;
import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import com.yunhwan.humtune.domain.audio.AudioMetaRepository;
import com.yunhwan.humtune.infrastructure.LocalAudioStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AudioUploadService {

	private final AudioMetaRepository audioMetaRepository;
	private final AnalysisRequestRepository analysisRequestRepository;
	private final LocalAudioStorage localAudioStorage;

	public AudioUploadService(
			AudioMetaRepository audioMetaRepository,
			AnalysisRequestRepository analysisRequestRepository,
			LocalAudioStorage localAudioStorage
	) {
		this.audioMetaRepository = audioMetaRepository;
		this.analysisRequestRepository = analysisRequestRepository;
		this.localAudioStorage = localAudioStorage;
	}

	@Transactional
	public AudioUploadResponse upload(MultipartFile file) {
		validate(file);

		String rawAudioPath = localAudioStorage.store(file);
		AudioMeta audioMeta = audioMetaRepository.save(new AudioMeta(
				file.getOriginalFilename(),
				file.getContentType(),
				file.getSize(),
				rawAudioPath
		));
		AnalysisRequest analysisRequest = analysisRequestRepository.save(new AnalysisRequest(audioMeta));

		return new AudioUploadResponse(
				audioMeta.getAudioId(),
				analysisRequest.getId(),
				analysisRequest.getStatus()
		);
	}

	private void validate(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file must not be empty");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("audio/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content type must be audio/*");
		}
	}
}
