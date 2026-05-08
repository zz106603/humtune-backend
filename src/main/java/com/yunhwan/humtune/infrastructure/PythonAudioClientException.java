package com.yunhwan.humtune.infrastructure;

public class PythonAudioClientException extends RuntimeException {

	public enum FailureCategory {
		PYTHON_HTTP_ERROR,
		PYTHON_TIMEOUT,
		PYTHON_NETWORK_ERROR,
		PYTHON_CLIENT_ERROR
	}

	private final FailureCategory failureCategory;

	public PythonAudioClientException(String message) {
		this(FailureCategory.PYTHON_CLIENT_ERROR, message);
	}

	public PythonAudioClientException(String message, Throwable cause) {
		this(FailureCategory.PYTHON_CLIENT_ERROR, message, cause);
	}

	public PythonAudioClientException(FailureCategory failureCategory, String message) {
		super(message);
		this.failureCategory = failureCategory;
	}

	public PythonAudioClientException(FailureCategory failureCategory, String message, Throwable cause) {
		super(message, cause);
		this.failureCategory = failureCategory;
	}

	public FailureCategory getFailureCategory() {
		return failureCategory;
	}
}
