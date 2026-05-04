package com.yunhwan.humtune.domain.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
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

	@Column(name = "adjusted_notes_json", nullable = false, columnDefinition = "text")
	private String adjustedNotesJson;

	@Column(name = "chords_json", nullable = false, columnDefinition = "text")
	private String chordsJson;

	@Column(name = "midi_path", nullable = false, length = 1024)
	private String midiPath;

	@Column(name = "feedback_text", columnDefinition = "text")
	private String feedbackText;

	@Column(name = "chord_explanation", columnDefinition = "text")
	private String chordExplanation;

	@Column(name = "naturalness_score")
	private Double naturalnessScore;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AnalysisResult() {
	}

	public AnalysisResult(
			AnalysisRequest analysisRequest,
			String detectedScale,
			String adjustedNotesJson,
			String chordsJson,
			String midiPath,
			String feedbackText,
			String chordExplanation,
			Double naturalnessScore
	) {
		this.analysisRequest = analysisRequest;
		this.detectedScale = detectedScale;
		this.adjustedNotesJson = adjustedNotesJson;
		this.chordsJson = chordsJson;
		this.midiPath = midiPath;
		this.feedbackText = feedbackText;
		this.chordExplanation = chordExplanation;
		this.naturalnessScore = naturalnessScore;
		this.createdAt = Instant.now();
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

	public String getAdjustedNotesJson() {
		return adjustedNotesJson;
	}

	public String getChordsJson() {
		return chordsJson;
	}

	public String getMidiPath() {
		return midiPath;
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
