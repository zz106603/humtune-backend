package com.yunhwan.humtune.domain.audio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "audio_meta")
public class AudioMeta {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long audioId;

	@Column(name = "original_file_name", length = 255)
	private String originalFileName;

	@Column(name = "content_type", length = 100)
	private String contentType;

	@Column(name = "file_size_bytes")
	private Long fileSizeBytes;

	@Column(name = "raw_audio_path", nullable = false, length = 1024)
	private String rawAudioPath;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AudioMeta() {
	}

	public AudioMeta(String originalFileName, String contentType, Long fileSizeBytes, String rawAudioPath) {
		this.originalFileName = originalFileName;
		this.contentType = contentType;
		this.fileSizeBytes = fileSizeBytes;
		this.rawAudioPath = rawAudioPath;
	}

	public Long getAudioId() {
		return audioId;
	}

	public String getOriginalFileName() {
		return originalFileName;
	}

	public String getContentType() {
		return contentType;
	}

	public Long getFileSizeBytes() {
		return fileSizeBytes;
	}

	public String getRawAudioPath() {
		return rawAudioPath;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
