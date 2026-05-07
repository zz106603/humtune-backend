package com.yunhwan.humtune.api.dto;

import com.yunhwan.humtune.domain.analysis.AnalysisStatus;
import java.time.Instant;

public record AudioStatusResponse(
		Long audioId,
		String filename,
		AnalysisStatus status,
		Instant createdAt,
		String errorMessage
) {
}
