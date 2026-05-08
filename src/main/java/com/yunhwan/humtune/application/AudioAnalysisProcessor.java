package com.yunhwan.humtune.application;

import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import com.yunhwan.humtune.domain.audio.AudioMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioAnalysisProcessor {

	private static final Logger log = LoggerFactory.getLogger(AudioAnalysisProcessor.class);

	private final AnalysisRequestRepository analysisRequestRepository;
	private final PythonAudioClient pythonAudioClient;
	private final String outputDirectory;

	public AudioAnalysisProcessor(
			AnalysisRequestRepository analysisRequestRepository,
			PythonAudioClient pythonAudioClient,
			@Value("${humtune.audio.output-directory:build/audio-outputs}") String outputDirectory
	) {
		this.analysisRequestRepository = analysisRequestRepository;
		this.pythonAudioClient = pythonAudioClient;
		this.outputDirectory = outputDirectory;
	}

	@Transactional
	public void process(Long analysisRequestId) {
		analysisRequestRepository.findById(analysisRequestId).ifPresentOrElse(
				analysisRequest -> {
					analysisRequest.markProcessing();
					AudioMeta audioMeta = analysisRequest.getAudioMeta();
					log.info(
							"Calling Python audio service. analysisRequestId={}, audioId={}, rawAudioPath={}, outputDirectory={}",
							analysisRequestId,
							audioMeta.getAudioId(),
							audioMeta.getRawAudioPath(),
							outputDirectory
					);
					pythonAudioClient.analyze(
							audioMeta.getAudioId(),
							audioMeta.getRawAudioPath(),
							outputDirectory
					);
				},
				() -> log.warn("AnalysisRequest not found. analysisRequestId={}", analysisRequestId)
		);
	}
}
