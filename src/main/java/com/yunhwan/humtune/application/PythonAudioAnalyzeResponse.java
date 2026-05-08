package com.yunhwan.humtune.application;

import com.fasterxml.jackson.databind.JsonNode;

public record PythonAudioAnalyzeResponse(
		String status,
		String detectedScale,
		Double keyConfidence,
		JsonNode originalNotes,
		JsonNode adjustedNotes,
		JsonNode chords,
		String midiPath,
		Long processingTimeMs,
		String errorMessage
) {
}
