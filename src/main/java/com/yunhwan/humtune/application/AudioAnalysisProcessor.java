package com.yunhwan.humtune.application;

import com.yunhwan.humtune.domain.analysis.AnalysisRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AudioAnalysisProcessor {

	private final AnalysisRequestRepository analysisRequestRepository;

	public AudioAnalysisProcessor(AnalysisRequestRepository analysisRequestRepository) {
		this.analysisRequestRepository = analysisRequestRepository;
	}

	@Transactional
	public void process(Long analysisRequestId) {
		analysisRequestRepository.findById(analysisRequestId)
				.ifPresent(analysisRequest -> analysisRequest.markProcessing());
	}
}
