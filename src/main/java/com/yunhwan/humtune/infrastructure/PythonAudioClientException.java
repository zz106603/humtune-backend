package com.yunhwan.humtune.infrastructure;

public class PythonAudioClientException extends RuntimeException {

	public PythonAudioClientException(String message) {
		super(message);
	}

	public PythonAudioClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
