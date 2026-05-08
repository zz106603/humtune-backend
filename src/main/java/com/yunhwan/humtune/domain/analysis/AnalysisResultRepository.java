package com.yunhwan.humtune.domain.analysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

	Optional<AnalysisResult> findByAnalysisRequest(AnalysisRequest analysisRequest);
}
