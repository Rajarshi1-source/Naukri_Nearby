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
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resume object storage on MinIO (S3-compatible). Resumes are PII (DPDP Act) — access is via
 * short-lived signed URLs only; we never serve raw bytes publicly (security-and-api.md §C).
 */
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
			minio.putObject(PutObjectArgs.builder()
					.bucket(props.bucket())
					.object(key)
					.stream(stream, content.length, -1)
					.contentType(contentType == null ? "application/octet-stream" : contentType)
					.build());
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
