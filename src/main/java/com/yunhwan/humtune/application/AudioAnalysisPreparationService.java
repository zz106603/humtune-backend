package com.yunhwan.humtune.application;

import com.yunhwan.humtune.domain.analysis.AnalysisRequest;
import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import com.yunhwan.humtune.infrastructure.LocalAudioStorage;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioAnalysisPreparationService {

	private final AnalysisRequestRepository analysisRequestRepository;
	private final LocalAudioStorage localAudioStorage;
	private final Path outputDirectory;

	public AudioAnalysisPreparationService(
			AnalysisRequestRepository analysisRequestRepository,
			LocalAudioStorage localAudioStorage,
			@Value("${humtune.audio.output-directory:storage/midi}") Path outputDirectory
	) {
		this.analysisRequestRepository = analysisRequestRepository;
		this.localAudioStorage = localAudioStorage;
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
							localAudioStorage.resolveForRead(audioMeta.getRawAudioPath()),
							resolveOutputDirectory()
					);
				});
	}

	private String resolveOutputDirectory() {
		return outputDirectory.toAbsolutePath().normalize().toString();
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
