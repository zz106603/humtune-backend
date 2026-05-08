package com.yunhwan.humtune.application;

public record PythonAudioAnalysisCommand(
		Long audioId,
		String rawAudioPath,
		String outputDirectory
) {
}
