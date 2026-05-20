package com.yunhwan.humtune.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AiFeedbackServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void AI_prompt는_deterministic_요약만_포함한다() throws Exception {
		AiFeedbackPrompt[] captured = new AiFeedbackPrompt[1];
		AiFeedbackService service = new AiFeedbackService(objectMapper, prompt -> {
			captured[0] = prompt;
			return "스케일 안에서 안정적으로 움직입니다.";
		});

		String feedback = service.generateFeedback(response());

		assertThat(feedback).isEqualTo("스케일 안에서 안정적으로 움직입니다.");
		assertThat(captured[0].systemPrompt()).contains("Do not generate melody");
		assertThat(captured[0].userPrompt()).contains("melodyMetrics");
		assertThat(captured[0].userPrompt()).contains("feedbackEvidence");
		assertThat(captured[0].userPrompt()).contains("adjustedNotesSummary");
		assertThat(captured[0].userPrompt()).contains("chordSummary");
		assertThat(captured[0].systemPrompt()).contains("melody interpretation feedback");
		assertThat(captured[0].systemPrompt()).contains("melody structure");
		assertThat(captured[0].userPrompt()).contains("repeated motif", "interval movement", "chord fit");
		assertThat(captured[0].userPrompt()).contains("what could change the feeling");
		assertThat(captured[0].userPrompt()).contains("Do not mention raw terms");
		assertThat(captured[0].userPrompt()).contains("Avoid vocal training advice");
		assertThat(captured[0].userPrompt()).doesNotContain("originalNotes");
	}

	@Test
	void AI_실패시_deterministic_fallback을_반환한다() throws Exception {
		AiFeedbackService service = new AiFeedbackService(objectMapper, prompt -> {
			throw new RuntimeException("ai failed");
		});

		String feedback = service.generateFeedback(responseWithEvidence("[{\"type\":\"rhythm\"}]"));

		assertThat(feedback).contains("앞으로 밀고 나가는");
		assertThat(feedback).contains("짧은 쉼표");
	}

	@Test
	void AI_설정이_없으면_음역_fallback을_반환한다() throws Exception {
		AiFeedbackService service = new AiFeedbackService(objectMapper, (AiFeedbackClient) null);

		String feedback = service.generateFeedback(responseWithNotes("[{\"pitch\":60},{\"pitch\":62}]"));

		assertThat(feedback).contains("가까운 음들");
		assertThat(feedback).contains("상승 흐름");
	}

	private PythonAudioAnalyzeResponse response() throws Exception {
		return responseWithEvidence("[{\"type\":\"scale\"}]");
	}

	private PythonAudioAnalyzeResponse responseWithEvidence(String feedbackEvidence) throws Exception {
		return new PythonAudioAnalyzeResponse(
				"COMPLETED",
				"C_MAJOR",
				0.9,
				objectMapper.readTree("[{\"pitch\":60}]"),
				objectMapper.readTree("[{\"pitch\":60},{\"pitch\":64}]"),
				objectMapper.readTree("[\"C\"]"),
				objectMapper.readTree("{\"scaleToneRatio\":0.8}"),
				objectMapper.readTree(feedbackEvidence),
				"storage/midi/sample.mid",
				null,
				123L,
				null
		);
	}

	private PythonAudioAnalyzeResponse responseWithNotes(String adjustedNotes) throws Exception {
		return new PythonAudioAnalyzeResponse(
				"COMPLETED",
				"C_MAJOR",
				0.9,
				objectMapper.readTree("[]"),
				objectMapper.readTree(adjustedNotes),
				objectMapper.readTree("[\"C\"]"),
				objectMapper.readTree("{}"),
				objectMapper.readTree("[]"),
				"storage/midi/sample.mid",
				null,
				123L,
				null
		);
	}
}
