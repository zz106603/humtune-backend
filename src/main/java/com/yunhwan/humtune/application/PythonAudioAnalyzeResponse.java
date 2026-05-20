package com.yunhwan.humtune.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PythonAudioAnalyzeResponse(
		String status,
		String detectedScale,
		Double keyConfidence,
		JsonNode originalNotes,
		JsonNode adjustedNotes,
		JsonNode chords,
		JsonNode melodyMetrics,
		JsonNode feedbackEvidence,
		String midiPath,
		String previewAudioPath,
		Long processingTimeMs,
		String errorMessage
) {
	public PythonAudioAnalyzeResponse(
			String status,
			String detectedScale,
			Double keyConfidence,
			JsonNode originalNotes,
			JsonNode adjustedNotes,
			JsonNode chords,
			String midiPath,
			String previewAudioPath,
			Long processingTimeMs,
			String errorMessage
	) {
		this(
				status,
				detectedScale,
				keyConfidence,
				originalNotes,
				adjustedNotes,
				chords,
				null,
				null,
				midiPath,
				previewAudioPath,
				processingTimeMs,
				errorMessage
		);
	}
}
