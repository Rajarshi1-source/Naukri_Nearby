package com.naukrinearby.service;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.naukrinearby.config.StorageProperties;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.ServerSideEncryptionS3;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resume object storage on MinIO (S3-compatible). Resumes are PII (DPDP Act) — access is via
 * short-lived signed URLs only; we never serve raw bytes publicly (security-and-api.md §C). With
 * {@code naukri.storage.encryption=sse} objects are written with SSE-S3 (encryption at rest); GETs
 * via signed URL stay transparent because the server decrypts. Requires MinIO to run with a KMS key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

	private final MinioClient minio;
	private final StorageProperties props;

	private volatile boolean bucketReady = false;

	public String upload(byte[] content, String originalFilename, String contentType) {
		ensureBucket();
		String key = "resumes/" + UUID.randomUUID() + extension(originalFilename);
		try (var stream = new ByteArrayInputStream(content)) {
			PutObjectArgs.Builder builder = PutObjectArgs.builder()
					.bucket(props.bucket())
					.object(key)
					.stream(stream, content.length, -1)
					.contentType(contentType == null ? "application/octet-stream" : contentType);
			if (props.sseEnabled()) {
				builder.sse(new ServerSideEncryptionS3());
			}
			minio.putObject(builder.build());
			return key;
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to upload resume to storage", ex);
		}
	}

	public byte[] download(String key) {
		try (var stream = minio.getObject(GetObjectArgs.builder()
				.bucket(props.bucket())
				.object(key)
				.build())) {
			return stream.readAllBytes();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to download resume from storage", ex);
		}
	}

	/** Right-to-erasure (DPDP): permanently removes a resume object. Missing keys are ignored. */
	public void delete(String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		try {
			minio.removeObject(RemoveObjectArgs.builder()
					.bucket(props.bucket())
					.object(key)
					.build());
		}
		catch (Exception ex) {
			log.warn("Failed to delete resume object {}: {}", key, ex.getMessage());
		}
	}

	public String signedUrl(String key) {
		try {
			return minio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
					.method(Method.GET)
					.bucket(props.bucket())
					.object(key)
					.expiry((int) props.signedUrlTtl().toSeconds(), TimeUnit.SECONDS)
					.build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to create signed URL", ex);
		}
	}

	private void ensureBucket() {
		if (bucketReady) {
			return;
		}
		synchronized (this) {
			if (bucketReady) {
				return;
			}
			try {
				boolean exists = minio.bucketExists(BucketExistsArgs.builder()
						.bucket(props.bucket())
						.build());
				if (!exists) {
					minio.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
				}
				bucketReady = true;
			}
			catch (Exception ex) {
				throw new IllegalStateException("Failed to ensure storage bucket exists", ex);
			}
		}
	}

	private static String extension(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		return dot >= 0 ? filename.substring(dot) : "";
	}
}
