package com.yunhwan.humtune.common;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

	public static final String AUDIO_ANALYSIS_EXECUTOR = "audioAnalysisExecutor";

	@Bean(name = AUDIO_ANALYSIS_EXECUTOR)
	public Executor audioAnalysisExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("audio-analysis-");
		executor.initialize();
		return executor;
	}
}
