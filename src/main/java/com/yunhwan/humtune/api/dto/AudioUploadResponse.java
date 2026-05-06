package com.yunhwan.humtune.api.dto;

import com.yunhwan.humtune.domain.analysis.AnalysisStatus;

public record AudioUploadResponse(
		Long audioId,
		Long analysisId,
		AnalysisStatus status
) {
}
