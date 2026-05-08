package com.yunhwan.humtune.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class LocalAudioStorageTest {

	@Test
	void 업로드_파일은_상대경로로_저장한다() {
		LocalAudioStorage localAudioStorage = new LocalAudioStorage(Path.of("storage/raw"));
		MockMultipartFile file = new MockMultipartFile("file", "sample.m4a", "audio/mp4", "audio".getBytes());

		String rawAudioPath = localAudioStorage.store(file);

		assertThat(Path.of(rawAudioPath).isAbsolute()).isFalse();
		assertThat(rawAudioPath.replace("\\", "/")).startsWith("storage/raw");
		localAudioStorage.delete(rawAudioPath);
	}

	@Test
	void Python_호출용_경로는_절대경로로_변환한다() {
		LocalAudioStorage localAudioStorage = new LocalAudioStorage(Path.of("storage/raw"));

		String resolvedPath = localAudioStorage.resolveForRead("storage/raw/sample.m4a");

		assertThat(Path.of(resolvedPath).isAbsolute()).isTrue();
		assertThat(resolvedPath.replace("\\", "/")).endsWith("/storage/raw/sample.m4a");
	}
}
