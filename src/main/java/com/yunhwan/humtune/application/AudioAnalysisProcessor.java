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
							try {
								pythonAudioClient.analyze(
										command.audioId(),
										command.rawAudioPath(),
										command.outputDirectory()
								);
								audioAnalysisPreparationService.markCompleted(analysisRequestId);
							} catch (RuntimeException ex) {
								log.warn(
										"Python audio analysis failed. analysisRequestId={}, audioId={}",
										analysisRequestId,
										command.audioId(),
										ex
								);
								audioAnalysisPreparationService.markFailed(
										analysisRequestId,
										"Python audio analysis failed: " + failureMessage(ex)
								);
							}
						},
						() -> log.warn("AnalysisRequest not found. analysisRequestId={}", analysisRequestId)
				);
	}

	private String failureMessage(RuntimeException ex) {
		if (ex.getMessage() == null || ex.getMessage().isBlank()) {
			return ex.getClass().getSimpleName();
		}
		return ex.getMessage();
	}
}
