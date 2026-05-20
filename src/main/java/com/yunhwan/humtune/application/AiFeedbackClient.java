package com.yunhwan.humtune.application;

public interface AiFeedbackClient {

	String generateFeedback(AiFeedbackPrompt prompt);
}
