package com.naukrinearby.service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import com.naukrinearby.exception.ResumeParseException;
import com.naukrinearby.generation.VisionProvider;
import com.naukrinearby.model.dto.ResumeParseResult;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Resume ingestion: store the file, extract text (PDF via PDFBox, image via the vision stub), run
 * the LLM extraction, and upsert the candidate profile — all off the request thread (master plan §6).
 * Parse status is tracked in Redis so the client can poll {@code GET /parse-status}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParserService {

	private static final Duration STATUS_TTL = Duration.ofHours(6);
	private static final String STATUS = "resume:status:";
	private static final String KEY = "resume:key:";
	private static final String USER = "resume:user:";

	private final FileStorageService storage;
	private final ResumeExtractionService extractionService;
	private final CandidateProfileService profileService;
	private final VisionProvider visionProvider;
	private final StringRedisTemplate redis;
	private final MeterRegistry meterRegistry;

	/** Stores the resume and registers a PENDING parse job. Returns the resumeId for polling. */
	public String store(Long userId, MultipartFile file) {
		byte[] content = readBytes(file);
		String fileKey = storage.upload(content, file.getOriginalFilename(), file.getContentType());
		String resumeId = UUID.randomUUID().toString();
		redis.opsForValue().set(KEY + resumeId, fileKey, STATUS_TTL);
		redis.opsForValue().set(USER + resumeId, String.valueOf(userId), STATUS_TTL);
		setStatus(resumeId, "PENDING");
		return resumeId;
	}

	public String status(String resumeId) {
		String s = redis.opsForValue().get(STATUS + resumeId);
		return s == null ? "UNKNOWN" : s;
	}

	@Async
	public void parseAsync(String resumeId) {
		setStatus(resumeId, "PROCESSING");
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			String fileKey = redis.opsForValue().get(KEY + resumeId);
			String userIdStr = redis.opsForValue().get(USER + resumeId);
			if (fileKey == null || userIdStr == null) {
				throw new ResumeParseException("Resume job expired or not found");
			}
			byte[] content = storage.download(fileKey);
			String text = extractText(content, fileKey);
			if (text == null || text.isBlank()) {
				throw new ResumeParseException("No extractable text in resume");
			}
			ResumeParseResult result = extractionService.extract(text);
			profileService.applyParsedResume(Long.valueOf(userIdStr), result, fileKey);
			setStatus(resumeId, "COMPLETED");
			log.info("Resume {} parsed: {} skills, {} months exp", resumeId,
					result.getSkills().size(), result.getTotalExperienceMonths());
		}
		catch (Exception ex) {
			setStatus(resumeId, "FAILED");
			log.error("Resume {} parse failed: {}", resumeId, ex.getMessage());
		}
		finally {
			sample.stop(meterRegistry.timer("resume_parse_duration_seconds"));
		}
	}

	/** Extracts plain text from a resume. PDFs use PDFBox; images fall back to the vision stub. */
	public String extractText(byte[] content, String fileKey) {
		String lower = fileKey == null ? "" : fileKey.toLowerCase();
		if (lower.endsWith(".pdf") || looksLikePdf(content)) {
			try (PDDocument doc = Loader.loadPDF(content)) {
				return new PDFTextStripper().getText(doc);
			}
			catch (IOException ex) {
				throw new ResumeParseException("Failed to read PDF", ex);
			}
		}
		if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return visionProvider.extractText(content, "Extract all text from this resume image.");
		}
		return new String(content, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static boolean looksLikePdf(byte[] content) {
		return content != null && content.length > 4
				&& content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
	}

	private void setStatus(String resumeId, String status) {
		redis.opsForValue().set(STATUS + resumeId, status, STATUS_TTL);
	}

	private static byte[] readBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException ex) {
			throw new ResumeParseException("Failed to read uploaded file", ex);
		}
	}
}
