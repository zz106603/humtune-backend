package com.yunhwan.humtune.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class AiFeedbackService {

	private static final Logger log = LoggerFactory.getLogger(AiFeedbackService.class);

	private final ObjectMapper objectMapper;
	private final AiFeedbackClient aiFeedbackClient;

	@Autowired
	public AiFeedbackService(ObjectMapper objectMapper, ObjectProvider<AiFeedbackClient> aiFeedbackClientProvider) {
		this(objectMapper, aiFeedbackClientProvider.getIfAvailable());
	}

	AiFeedbackService(ObjectMapper objectMapper, AiFeedbackClient aiFeedbackClient) {
		this.objectMapper = objectMapper;
		this.aiFeedbackClient = aiFeedbackClient;
	}

	public String generateFeedback(PythonAudioAnalyzeResponse response) {
		try {
			AiFeedbackPrompt prompt = buildPrompt(response);
			if (aiFeedbackClient == null) {
				log.info("AI feedback client is disabled or unavailable. Using deterministic fallback feedback.");
				return fallbackFeedback(response);
			}
			String feedback = aiFeedbackClient.generateFeedback(prompt);
			if (feedback == null || feedback.isBlank()) {
				log.info("AI feedback client returned no feedback. Using deterministic fallback feedback.");
				return fallbackFeedback(response);
			}
			return feedback.strip();
		} catch (RuntimeException ex) {
			log.warn("AI feedback generation failed. Using deterministic fallback feedback. exceptionType={}", ex.getClass().getSimpleName());
			return fallbackFeedback(response);
		}
	}

	AiFeedbackPrompt buildPrompt(PythonAudioAnalyzeResponse response) {
		Map<String, Object> summary = new LinkedHashMap<>();
		summary.put("detectedScale", response.detectedScale());
		summary.put("melodyMetrics", response.melodyMetrics());
		summary.put("feedbackEvidence", response.feedbackEvidence());
		summary.put("adjustedNotesSummary", summarizeNotes(response.adjustedNotes()));
		summary.put("chordSummary", summarizeChords(response.chords()));

		return new AiFeedbackPrompt(
				"""
				You explain deterministic HumTune analysis results.
				Do not generate melody, notes, chords, scales, MIDI, or arrangement ideas.
				Do not override or question deterministic decisions.
				Write concise Korean coaching feedback grounded only in the provided metrics and evidence.
				""".strip(),
				"""
				Use only this deterministic summary.
				Return only user-facing feedback text in 2-4 concise Korean sentences.
				Avoid vague praise, promotion, and instructions to change to specific new notes/chords/scales.

				%s
				""".formatted(writeJson(summary)).strip()
		);
	}

	private Map<String, Object> summarizeNotes(JsonNode adjustedNotes) {
		Map<String, Object> summary = new LinkedHashMap<>();
		if (adjustedNotes == null || !adjustedNotes.isArray()) {
			summary.put("noteCount", 0);
			return summary;
		}

		int noteCount = 0;
		Integer minPitch = null;
		Integer maxPitch = null;
		for (JsonNode note : adjustedNotes) {
			noteCount++;
			JsonNode pitchNode = note.get("pitch");
			if (pitchNode != null && pitchNode.canConvertToInt()) {
				int pitch = pitchNode.asInt();
				minPitch = minPitch == null ? pitch : Math.min(minPitch, pitch);
				maxPitch = maxPitch == null ? pitch : Math.max(maxPitch, pitch);
			}
		}
		summary.put("noteCount", noteCount);
		if (minPitch != null && maxPitch != null) {
			summary.put("minPitch", minPitch);
			summary.put("maxPitch", maxPitch);
			summary.put("pitchRangeSemitones", maxPitch - minPitch);
		}
		return summary;
	}

	private Map<String, Object> summarizeChords(JsonNode chords) {
		Map<String, Object> summary = new LinkedHashMap<>();
		if (chords == null || !chords.isArray()) {
			summary.put("chordCount", 0);
			return summary;
		}
		summary.put("chordCount", chords.size());
		summary.put("chords", chords);
		return summary;
	}

	private String fallbackFeedback(PythonAudioAnalyzeResponse response) {
		String metrics = jsonText(response.melodyMetrics()).toLowerCase();
		String evidence = jsonText(response.feedbackEvidence()).toLowerCase();
		String combined = metrics + " " + evidence;
		Map<String, Object> noteSummary = summarizeNotes(response.adjustedNotes());

		if (combined.contains("rhythm") || combined.contains("timing") || combined.contains("quant")) {
			return "리듬이 일정하지 않은 구간이 감지되었습니다. 박자를 조금 더 고르게 유지하면 멜로디가 더 안정적으로 들립니다.";
		}
		if (combined.contains("narrow") || ((Integer) noteSummary.getOrDefault("pitchRangeSemitones", 99)) <= 3) {
			return "멜로디의 음역이 좁게 형성되어 차분하고 단순한 인상을 줍니다. 같은 흐름을 유지하되 프레이즈의 높낮이를 조금 더 분명히 느끼며 불러보면 좋습니다.";
		}
		if (combined.contains("repeat") || combined.contains("motif")) {
			return "반복되는 모티프가 감지되어 멜로디의 기억하기 쉬운 특징이 있습니다. 반복의 길이와 박자를 일정하게 유지하면 더 또렷하게 전달됩니다.";
		}
		return "멜로디가 비교적 안정적인 흐름으로 분석되었습니다. 단순한 리듬과 현재 스케일 중심을 유지하면 자연스럽게 들립니다.";
	}

	private String jsonText(JsonNode jsonNode) {
		if (jsonNode == null || jsonNode.isNull()) {
			return "";
		}
		return jsonNode.toString();
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize AI feedback prompt", ex);
		}
	}
}
