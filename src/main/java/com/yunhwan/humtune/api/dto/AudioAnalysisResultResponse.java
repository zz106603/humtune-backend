package com.yunhwan.humtune.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.yunhwan.humtune.domain.analysis.AnalysisStatus;

public record AudioAnalysisResultResponse(
		Long audioId,
		AnalysisStatus status,
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
}
