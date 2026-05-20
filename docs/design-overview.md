# HumTune - Design Overview

## 0. 사용 지침

이 설계는 아래 원칙을 반드시 유지해야 한다.

- 설계를 변경하지 말 것
- AI 역할을 확대하지 말 것
- 규칙 기반 파이프라인을 제거하지 말 것
- MVP 범위를 확장하지 말 것

이 문서를 기반으로 작업할 때는 “수정”이 아니라 “보완”만 허용된다.

---

## 1. 프로젝트 개요

### 한 줄 정의

사용자의 허밍을 분석하여 멜로디를 보정하고,
어울리는 코드와 피아노 반주를 생성하는 AI-assisted 음악 보조 시스템

---

## 2. 핵심 설계 철학

### 2.1 Deterministic Pipeline

동일 입력 → 동일 결과를 보장해야 한다.

오디오 입력 → 분석 → 규칙 기반 처리 → MIDI 생성

---

### 2.2 AI 역할 (최종 정의)

AI는 음악을 생성하지 않는다.
AI는 deterministic 분석 결과와 근거를 **멜로디 해석과 작곡 방향 문장으로 변환**하는 역할만 수행한다.

AI의 역할:

- 코드 및 결과 설명 생성
- 사용자 허밍 피드백 생성
- 생성된 결과의 자연스러움 평가
- deterministic 지표를 사용자 친화적인 설명으로 변환
- 분석 근거 기반의 멜로디 해석 피드백 생성

---

### 2.3 AI 금지 영역

AI는 절대 수행하지 않는다:

- pitch 추출
- 음정 보정
- 박자 보정
- scale/chord 단독 결정
- scale/chord 후보 선택
- note 수정
- melody 생성
- MIDI 생성

→ 모든 생성 로직은 시스템 코드가 담당한다

---

## 3. MVP 범위

### 포함

- 허밍 업로드 (5~10초)
- pitch detection
- note sequence 변환
- scale fitting
- quantization
- chord 생성
- MIDI 생성
- 결과 재생

### 제외

- 완성곡 생성
- 보컬 합성
- 다중 악기 편곡
- 실시간 처리

---

## 4. 실행 모델 (확정)

### 4.1 비동기 처리

POST /api/audio

1. 파일 저장
2. AnalysisRequest 생성 (PENDING)
3. 즉시 응답 반환

이후:

- async worker 실행
- PROCESSING 전환
- Python Audio Service 호출

---

### 4.2 상태 전이

- PENDING → PROCESSING → COMPLETED
- PENDING → PROCESSING → FAILED

---

### 4.3 요청-응답 규칙

- API는 Python 분석을 기다리지 않는다
- 결과는 polling으로 조회한다

---

## 5. 상태 모델

- PENDING
- PROCESSING
- COMPLETED
- FAILED

---

## 6. 핵심 처리 흐름

Upload
→ AnalysisRequest 생성 (PENDING)
→ Async worker 실행
→ PROCESSING
→ Python 분석
→ Rule 기반 결과 생성
→ Melody quality metric 계산
→ Feedback evidence 생성
→ AI 피드백 설명 생성
→ 결과 저장
→ COMPLETED

실패 시:

→ FAILED
→ errorMessage 저장

---

## 7. Deterministic 규칙

### 7.1 Scale 선택

- major/minor 후보 생성
- note distance 합 최소 선택

tie-break:

1. scale tone 포함 비율
2. tonic 포함 여부
3. C Major

---

### 7.2 Tempo

- pitch 간 시간 간격 기반 추정

fallback:

- 100 BPM

---

### 7.3 Quantization

- 1/8 note grid
- nearest grid

---

### 7.4 Chord 선택

- diatonic chord 후보
- melody 포함 비율 scoring

tie-break:

1. tonic
2. dominant
3. subdominant

---

### 7.5 Progression

- 3~4 chord
- 시작: tonic
- 종료: tonic 또는 dominant

---

### 7.6 Melody Quality Metrics

피드백에 필요한 품질 지표는 deterministic 코드가 계산한다.

지표 방향:

- scale tone 포함 비율
- pitch 안정성
- 음역 범위
- 큰 도약 빈도
- 반복 패턴
- 박자 grid 정렬 정도
- chord tone 포함 비율

AI는 지표를 계산하지 않는다.
AI는 계산된 지표를 설명 문장으로 바꾼다.

---

### 7.7 Feedback Evidence

AI 피드백은 반드시 시스템이 생성한 evidence를 근거로 한다.

evidence 예시:

- scale fitting 결과와 scale 밖 음 비율
- quantization 전후 timing 차이
- melody interval 분포
- chord와 melody note의 매칭 비율
- 반복되는 motif 후보
- 불안정하거나 개선 여지가 있는 구간

evidence는 판단 근거이며 생성 명령이 아니다.
AI는 evidence 밖의 음악적 결정을 새로 만들지 않는다.

---

## 8. 실패 처리

### 8.1 Pitch 실패

→ 재시도 → 실패 시 단일 note melody

---

### 8.2 Note 실패

→ default melody 생성
→ COMPLETED 유지

---

### 8.3 Chord 실패

→ 기본 progression

C - F - G - C

---

### 8.4 MIDI 실패

→ FAILED

---

### 8.5 AI 실패

→ COMPLETED 유지
→ deterministic fallback feedbackText 저장

---

## 9. Python Audio Service 계약

### Request

POST /internal/audio/analyze

- audioId
- rawAudioPath
- outputDirectory

---

### Response

성공:

- status: COMPLETED
- detectedScale
- originalNotes: Basic Pitch raw notes, 진단/호환용
- adjustedNotes: 최종 quantized melody notes, 기존 API 호환을 위해 필드명 유지
- chords: chord label sequence only
- melodyMetrics: deterministic 품질 지표
- feedbackEvidence: deterministic 피드백 근거
- midiPath: 최종 산출물 MIDI 파일 경로
- previewAudioPath

chord timing은 `chords`에 노출하지 않고 MIDI 파일에 반영한다.
알 수 없는 추가 필드는 Spring 역직렬화 실패를 일으키지 않아야 한다.

실패:

- status: FAILED
- errorMessage

---

### 입력 조건

- Spring이 파일 존재 보장
- Python이 읽기 검증
- 내부에서 오디오 표준화 수행

---

### 재실행 규칙

- 동일 audioId → overwrite 허용
- 결과는 deterministic

---

## 10. AI Assistant 계약

### 입력

- detectedScale
- adjustedNotes: 최종 quantized melody notes
- chords: chord label sequence
- melodyQualityMetrics: deterministic 품질 지표
- feedbackEvidence: deterministic 근거 목록

---

### 출력

- feedbackText
- chordExplanation
- naturalnessScore

---

### 원칙

- AI는 결과를 생성하지 않는다
- AI는 melody, note, scale, chord, MIDI를 변경하지 않는다
- AI는 deterministic 결과를 설명하고 멜로디 분위기, 코드 어울림, 편곡 방향만 작성한다
- AI 응답은 evidence 기반이어야 한다
- evidence에 없는 결론은 추측으로 확장하지 않는다
- AI 입력은 melodyMetrics, feedbackEvidence, detectedScale, adjustedNotes summary, chord summary로 제한한다
- AI 호출은 Gemini generateContent를 사용하며 API key/model은 환경 변수로만 주입한다
- AI 출력은 쉬운 한국어 멜로디 해석 문장이어야 하며 raw metric 이름, JSON 필드명, 점수 나열을 노출하지 않는다
- AI 출력은 반복, 음 간격, 음역, 흐름, chord fit 중 최소 하나의 구조적 근거로 분위기와 확장 방향을 설명한다
- AI 실패는 전체 실패가 아니다
- AI 실패 또는 미설정 시 deterministic fallback feedbackText를 저장한다

---

### 향후 피드백 파이프라인

deterministic analysis
→ melody quality metrics
→ feedback evidence
→ AI feedback explanation

이 흐름은 음악 생성 파이프라인이 아니다.
피드백 생성은 분석 결과를 해석하는 후처리 단계다.

---

## 11. Timeout / 장애 처리

- Python 연결 timeout: 3초
- Python 응답 timeout: 120초
- timeout 시 FAILED

- PROCESSING은 Python 응답 timeout 기준으로 실패 전환

### 판정 주체

- Spring async worker

### 처리

- Python 응답 timeout 초과 시 FAILED

---

## 12. API 구조

POST /api/audio
GET /api/audio/{audioId}
GET /api/audio/{audioId}/result
GET /api/audio/{audioId}/files/preview
GET /api/audio/{audioId}/files/midi

---

## 13. 저장 구조

### DB

- audio_meta
- analysis_request
- analysis_result
- melodyMetrics / feedbackEvidence는 analysis_result에 JSON 문자열로 저장한다
- feedbackText는 AI 또는 deterministic fallback으로 저장한다
- chordExplanation / naturalnessScore 생성은 후속 단계로 둔다

---

### 파일

- raw audio
- MIDI
- preview audio

---

## 14. 아키텍처

Frontend
→ Spring Boot
→ Python Audio Service
→ Local Storage
→ PostgreSQL

---

### 14.1 책임 분리

Spring Boot:

- 업로드, 상태 전이, timeout, 저장 orchestration
- Python 분석 결과 저장
- AI 피드백 호출 및 실패 격리

Python Audio Service:

- Basic Pitch 실행
- deterministic cleanup
- scale fitting
- quantization
- chord inference
- MIDI/preview 생성
- melody quality metrics 계산
- feedback evidence 생성

AI Assistant:

- deterministic 결과 설명
- evidence 기반 코칭 피드백 작성
- 사용자 친화적 설명 생성

Frontend:

- 업로드와 polling
- MIDI/preview 재생
- 분석 결과와 AI 피드백 표시

---

## 15. 성능 기준

- 10초 이내 결과

---

## 16. 테스트 기준

- 정상 입력
- 잡음 입력
- pitch 실패
- chord 실패
- AI 실패
- timeout

---

## 17. 절대 변경 금지

1. AI가 음악 생성 금지
2. rule 기반 제거 금지
3. pipeline 단순화 금지
4. MVP 확장 금지

---

## 최종 요약

- deterministic pipeline
- rule-based generation
- async processing
- controlled failure
- AI = evidence 기반 설명 + 코칭 피드백
