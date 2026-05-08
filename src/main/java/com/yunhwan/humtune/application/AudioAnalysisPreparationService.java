package com.yunhwan.humtune.application;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioAnalysisPreparationService {

	private final AnalysisRequestRepository analysisRequestRepository;
	private final String outputDirectory;

	public AudioAnalysisPreparationService(
			AnalysisRequestRepository analysisRequestRepository,
			@Value("${humtune.audio.output-directory:build/audio-outputs}") String outputDirectory
	) {
		this.analysisRequestRepository = analysisRequestRepository;
		this.outputDirectory = outputDirectory;
	}

	@Transactional
	public Optional<PythonAudioAnalysisCommand> markProcessing(Long analysisRequestId) {
		return analysisRequestRepository.findById(analysisRequestId)
				.map(analysisRequest -> {
					analysisRequest.markProcessing();
					AudioMeta audioMeta = analysisRequest.getAudioMeta();
					return new PythonAudioAnalysisCommand(
							audioMeta.getAudioId(),
							audioMeta.getRawAudioPath(),
							outputDirectory
					);
				});
	}

	@Transactional
	public void markCompleted(Long analysisRequestId) {
		analysisRequestRepository.findById(analysisRequestId)
				.ifPresent(AnalysisRequest::markCompleted);
	}

	@Transactional
	public void markFailed(Long analysisRequestId, String errorMessage) {
		analysisRequestRepository.findById(analysisRequestId)
				.ifPresent(analysisRequest -> analysisRequest.markFailed(errorMessage));
	}
}
