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
				Use metrics and evidence only as internal reasoning input.
				Never expose raw metric names, JSON field names, counts, or numeric scores to the user.
				Write plain Korean melody interpretation feedback for a person developing melody ideas.
				Every feedback must explain why the melody feels that way using melody structure.
				Do not give singing, vocal, pitch correction, metronome, or practice accuracy advice.
				""".strip(),
				"""
				Use only this deterministic summary.
				Return only user-facing feedback text in 2-4 concise Korean sentences.
				Ground the interpretation in at least one concrete structural reason: repeated motif, small or wide interval movement, melodic range, phrase direction, chord fit, or stable/dynamic flow.
				Explain what feeling that structure creates and what could change the feeling if the melody is expanded.
				Do not mention raw terms such as offGridNoteCount, chordToneAlignment, pitchStability, rhythmConsistency, JSON, metric, score, or evidence.
				Do not list numbers unless they are musically necessary.
				Avoid generic praise such as "interesting", "good", "positive", or broad emotional claims without a structural reason.
				Avoid vocal training advice, metronome practice advice, and instructions to change to specific new notes/chords/scales.

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
			return "멜로디의 흐름이 일정하게 머무르기보다 앞으로 밀고 나가는 지점이 있어 약간 유동적인 인상을 줍니다. 이 움직임은 코드 위에서 정적인 배경보다 리듬감 있는 피아노나 기타 패턴과 붙었을 때 더 잘 살아납니다. 확장할 때는 핵심 구절 뒤에 짧은 쉼표를 두면 다음 멜로디가 들어올 자리가 더 분명해집니다.";
		}
		if (combined.contains("narrow") || ((Integer) noteSummary.getOrDefault("pitchRangeSemitones", 99)) <= 3) {
			return "멜로디가 가까운 음들 사이에서 움직여 차분하고 안정적인 분위기를 만듭니다. 코드 진행과 붙이면 큰 긴장보다 부드러운 중심감이 먼저 느껴지는 타입이라, 미니멀한 반주나 잔잔한 발라드 질감에 잘 어울립니다. 후렴이나 두 번째 구절에서는 한 번쯤 더 큰 상승 흐름을 넣으면 같은 분위기 안에서도 더 뚜렷한 전환이 생깁니다.";
		}
		if (combined.contains("repeat") || combined.contains("motif")) {
			return "짧게 반복되는 모양이 있어 멜로디가 기억에 남는 훅처럼 작동합니다. 이 반복은 코드 위에서 곡의 중심 아이디어가 될 수 있으므로, 반주는 복잡하게 움직이기보다 모티프가 들릴 공간을 남기는 편이 좋습니다. 다음 구간에서는 같은 모양을 살짝 높이거나 낮춰 변주하면 익숙함은 유지하면서도 흐름이 확장됩니다.";
		}
		return "멜로디가 큰 도약보다 부드러운 흐름을 중심으로 이어져 안정적인 인상을 만듭니다. 현재 코드와는 무리 없이 맞물리는 중심감이 있어, 반주는 멜로디를 밀어내기보다 공간을 남기며 받쳐주는 방향이 어울립니다. 곡으로 확장한다면 마지막 구절 근처에 조금 더 높은 움직임을 한 번 배치해 차분한 흐름에 작은 전환점을 만들 수 있습니다.";
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
