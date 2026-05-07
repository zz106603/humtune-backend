package com.yunhwan.humtune.domain.analysis;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRequestRepository extends JpaRepository<AnalysisRequest, Long> {

	Optional<AnalysisRequest> findByAudioMeta_AudioId(Long audioId);
}
