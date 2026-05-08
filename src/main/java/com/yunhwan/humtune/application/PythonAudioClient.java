package com.yunhwan.humtune.application;

public interface PythonAudioClient {

	PythonAudioAnalyzeResponse analyze(Long audioId, String rawAudioPath, String outputDirectory);
}
