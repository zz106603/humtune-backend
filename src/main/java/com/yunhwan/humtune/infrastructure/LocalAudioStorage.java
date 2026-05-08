package com.yunhwan.humtune.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LocalAudioStorage {

	private final Path storageRoot;

	public LocalAudioStorage(@Value("${humtune.audio.storage-path:storage/raw}") Path storageRoot) {
		this.storageRoot = storageRoot;
	}

	public String store(MultipartFile file) {
		try {
			Files.createDirectories(storageRoot);
			Path storedPath = storageRoot.resolve(UUID.randomUUID() + extensionOf(file.getOriginalFilename()));
			file.transferTo(storedPath);
			return storedPath.toString();
		} catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store audio file", ex);
		}
	}

	public String resolveForRead(String rawAudioPath) {
		Path path = Path.of(rawAudioPath);
		if (path.isAbsolute()) {
			return path.normalize().toAbsolutePath().toString();
		}
		return path.toAbsolutePath().normalize().toString();
	}

	public void delete(String rawAudioPath) {
		try {
			Files.deleteIfExists(Path.of(rawAudioPath));
		} catch (IOException ignored) {
		}
	}

	private String extensionOf(String filename) {
		if (filename == null) {
			return "";
		}
		String name = Path.of(filename).getFileName().toString();
		int dotIndex = name.lastIndexOf('.');
		if (dotIndex < 0) {
			return "";
		}
		return name.substring(dotIndex);
	}
}
