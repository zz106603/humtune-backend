package com.yunhwan.humtune.domain.analysis;

import com.yunhwan.humtune.domain.audio.AudioMeta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "analysis_request")
public class AnalysisRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "audio_meta_id", nullable = false, unique = true)
	private AudioMeta audioMeta;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private AnalysisStatus status;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "processing_started_at")
	private Instant processingStartedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "failed_at")
	private Instant failedAt;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	protected AnalysisRequest() {
	}

	public AnalysisRequest(AudioMeta audioMeta) {
		this.audioMeta = audioMeta;
		this.status = AnalysisStatus.PENDING;
		this.requestedAt = Instant.now();
	}

	public void markProcessing() {
		this.status = AnalysisStatus.PROCESSING;
		this.processingStartedAt = Instant.now();
		this.errorMessage = null;
	}

	public void markCompleted() {
		this.status = AnalysisStatus.COMPLETED;
		this.completedAt = Instant.now();
		this.failedAt = null;
		this.errorMessage = null;
	}

	public void markFailed(String errorMessage) {
		this.status = AnalysisStatus.FAILED;
		this.failedAt = Instant.now();
		this.errorMessage = errorMessage;
	}

	public Long getId() {
		return id;
	}

	public AudioMeta getAudioMeta() {
		return audioMeta;
	}

	public AnalysisStatus getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public Instant getProcessingStartedAt() {
		return processingStartedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public Instant getFailedAt() {
		return failedAt;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
