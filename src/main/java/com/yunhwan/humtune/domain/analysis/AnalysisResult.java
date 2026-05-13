package com.yunhwan.humtune.domain.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Builder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "analysis_result")
public class AnalysisResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "analysis_request_id", nullable = false, unique = true)
	private AnalysisRequest analysisRequest;

	@Column(name = "detected_scale", nullable = false, length = 50)
	private String detectedScale;

	@Column(name = "key_confidence")
	private Double keyConfidence;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "original_notes_json", nullable = false, columnDefinition = "jsonb")
	private String originalNotesJson;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "adjusted_notes_json", nullable = false, columnDefinition = "jsonb")
	private String adjustedNotesJson;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "chords_json", nullable = false, columnDefinition = "jsonb")
	private String chordsJson;

	@Column(name = "midi_path", nullable = false, length = 1024)
	private String midiPath;

	@Column(name = "preview_audio_path", length = 1024)
	private String previewAudioPath;

	@Column(name = "processing_time_ms")
	private Long processingTimeMs;

	@Column(name = "feedback_text", columnDefinition = "text")
	private String feedbackText;

	@Column(name = "chord_explanation", columnDefinition = "text")
	private String chordExplanation;

	@Column(name = "naturalness_score")
	private Double naturalnessScore;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AnalysisResult() {
	}

	@Builder
	public AnalysisResult(
			AnalysisRequest analysisRequest,
			String detectedScale,
			Double keyConfidence,
			String originalNotesJson,
			String adjustedNotesJson,
			String chordsJson,
			String midiPath,
			String previewAudioPath,
			Long processingTimeMs,
			String feedbackText,
			String chordExplanation,
			Double naturalnessScore
	) {
		this.analysisRequest = analysisRequest;
		this.detectedScale = detectedScale;
		this.keyConfidence = keyConfidence;
		this.originalNotesJson = originalNotesJson;
		this.adjustedNotesJson = adjustedNotesJson;
		this.chordsJson = chordsJson;
		this.midiPath = midiPath;
		this.previewAudioPath = previewAudioPath;
		this.processingTimeMs = processingTimeMs;
		this.feedbackText = feedbackText;
		this.chordExplanation = chordExplanation;
		this.naturalnessScore = naturalnessScore;
	}

	public Long getId() {
		return id;
	}

	public AnalysisRequest getAnalysisRequest() {
		return analysisRequest;
	}

	public String getDetectedScale() {
		return detectedScale;
	}

	public Double getKeyConfidence() {
		return keyConfidence;
	}

	public String getOriginalNotesJson() {
		return originalNotesJson;
	}

	public String getAdjustedNotesJson() {
		return adjustedNotesJson;
	}

	public String getChordsJson() {
		return chordsJson;
	}

	public String getMidiPath() {
		return midiPath;
	}

	public String getPreviewAudioPath() {
		return previewAudioPath;
	}

	public Long getProcessingTimeMs() {
		return processingTimeMs;
	}

	public String getFeedbackText() {
		return feedbackText;
	}

	public String getChordExplanation() {
		return chordExplanation;
	}

	public Double getNaturalnessScore() {
		return naturalnessScore;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
