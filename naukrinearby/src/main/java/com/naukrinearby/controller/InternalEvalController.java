package com.naukrinearby.controller;

import com.naukrinearby.config.EvalProperties;
import com.naukrinearby.exception.ForbiddenException;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.service.ResumeExtractionService;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eval-only endpoint the Python harness calls to exercise the EXACT production extraction path
 * (same prompt + adapter + post-processing) without auth, MinIO, or DB (eval connectors §5).
 *
 * <p>Defense in depth: the bean only exists when {@code naukri.eval.enabled=true}, and every call must
 * present the matching {@code X-Eval-Key}. NEVER enable in production.
 */
@RestController
@RequestMapping("/api/internal/eval")
@ConditionalOnProperty(name = "naukri.eval.enabled", havingValue = "true")
@RequiredArgsConstructor
public class InternalEvalController {

	private final ResumeExtractionService extractionService;
	private final EvalProperties evalProps;

	@PostMapping("/parse")
	public ResumeParseResult parse(@RequestHeader(value = "X-Eval-Key", required = false) String key,
			@RequestBody EvalParseRequest req) {
		if (evalProps.apiKey() == null || evalProps.apiKey().isBlank() || !evalProps.apiKey().equals(key)) {
			throw new ForbiddenException("Invalid eval key");
		}
		return extractionService.extract(req.resumeText());
	}

	public record EvalParseRequest(@NotBlank @JsonProperty("resume_text") String resumeText) {
	}
}
