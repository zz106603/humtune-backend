package com.yunhwan.humtune.application;

import com.yunhwan.humtune.common.AsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AudioAnalysisEventListener {

	private final AudioAnalysisProcessor audioAnalysisProcessor;

	public AudioAnalysisEventListener(AudioAnalysisProcessor audioAnalysisProcessor) {
		this.audioAnalysisProcessor = audioAnalysisProcessor;
	}

	@Async(AsyncConfig.AUDIO_ANALYSIS_EXECUTOR)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void handle(AnalysisRequestedEvent event) {
		audioAnalysisProcessor.process(event.analysisRequestId());
	}
}
