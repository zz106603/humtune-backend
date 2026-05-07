package com.yunhwan.humtune.application;

import com.yunhwan.humtune.api.dto.AudioStatusResponse;
import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AudioStatusService {

	private final AnalysisRequestRepository analysisRequestRepository;

	public AudioStatusService(AnalysisRequestRepository analysisRequestRepository) {
		this.analysisRequestRepository = analysisRequestRepository;
	}

	@Transactional(readOnly = true)
	public AudioStatusResponse getStatus(Long audioId) {
		AnalysisRequest analysisRequest = analysisRequestRepository.findByAudioMeta_AudioId(audioId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Audio not found"));
		AudioMeta audioMeta = analysisRequest.getAudioMeta();

		return new AudioStatusResponse(
				audioMeta.getAudioId(),
				audioMeta.getOriginalFileName(),
				analysisRequest.getStatus(),
				audioMeta.getCreatedAt(),
				analysisRequest.getErrorMessage()
		);
	}
}
