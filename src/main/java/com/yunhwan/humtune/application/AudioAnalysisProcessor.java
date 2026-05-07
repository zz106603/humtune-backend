package com.yunhwan.humtune.application;

import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioAnalysisProcessor {

	private static final Logger log = LoggerFactory.getLogger(AudioAnalysisProcessor.class);

	private final AnalysisRequestRepository analysisRequestRepository;

	public AudioAnalysisProcessor(AnalysisRequestRepository analysisRequestRepository) {
		this.analysisRequestRepository = analysisRequestRepository;
	}

	@Transactional
	public void process(Long analysisRequestId) {
		analysisRequestRepository.findById(analysisRequestId).ifPresentOrElse(
				analysisRequest -> analysisRequest.markProcessing(),
				() -> log.warn("AnalysisRequest not found. analysisRequestId={}", analysisRequestId)
		);
	}
}
