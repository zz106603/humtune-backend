package com.yunhwan.humtune.infrastructure;

public record PythonAudioAnalyzeRequest(
		String audioId,
		String rawAudioPath,
		String outputDirectory
) {
}
