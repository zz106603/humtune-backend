package com.yunhwan.humtune.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AudioAnalysisProcessor {

	private static final Logger log = LoggerFactory.getLogger(AudioAnalysisProcessor.class);

	private final AudioAnalysisPreparationService audioAnalysisPreparationService;
	private final PythonAudioClient pythonAudioClient;

	public AudioAnalysisProcessor(
			AudioAnalysisPreparationService audioAnalysisPreparationService,
			PythonAudioClient pythonAudioClient
	) {
		this.audioAnalysisPreparationService = audioAnalysisPreparationService;
		this.pythonAudioClient = pythonAudioClient;
	}

	public void process(Long analysisRequestId) {
		audioAnalysisPreparationService.markProcessing(analysisRequestId)
				.ifPresentOrElse(
						command -> {
							log.info(
									"Calling Python audio service. analysisRequestId={}, audioId={}, rawAudioPath={}, outputDirectory={}",
									analysisRequestId,
									command.audioId(),
									command.rawAudioPath(),
									command.outputDirectory()
							);
							pythonAudioClient.analyze(
									command.audioId(),
									command.rawAudioPath(),
									command.outputDirectory()
							);
						},
						() -> log.warn("AnalysisRequest not found. analysisRequestId={}", analysisRequestId)
				);
	}
}
