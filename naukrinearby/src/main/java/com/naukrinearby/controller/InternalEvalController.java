package com.naukrinearby.controller;

import java.util.List;

import com.naukrinearby.config.EvalProperties;
import com.naukrinearby.exception.ForbiddenException;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
import com.naukrinearby.model.dto.search.SearchResultItem;
import com.naukrinearby.service.ResumeExtractionService;
import com.naukrinearby.service.SearchService;
import com.naukrinearby.service.TranslationService;

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
	private final SearchService searchService;
	private final TranslationService translationService;
	private final EvalProperties evalProps;

	@PostMapping("/parse")
	public ResumeParseResult parse(@RequestHeader(value = "X-Eval-Key", required = false) String key,
			@RequestBody EvalParseRequest req) {
		requireKey(key);
		return extractionService.extract(req.resumeText());
	}

	/** Match-quality eval: returns the ranked job ids for a query so the harness can score recall@10/MRR. */
	@PostMapping("/match")
	public EvalMatchResponse match(@RequestHeader(value = "X-Eval-Key", required = false) String key,
			@RequestBody EvalMatchRequest req) {
		requireKey(key);
		SearchQuery query = new SearchQuery(req.query(), req.lat(), req.lng(), req.radiusKm(),
				req.category(), req.size() == null ? 10 : req.size());
		SearchResponse response = searchService.search(query);
		List<Long> ids = response.results().stream().map(SearchResultItem::id).toList();
		return new EvalMatchResponse(ids, response.source());
	}

	/** Translation-quality eval: returns the translated text so the harness can spot-check regional output. */
	@PostMapping("/translate")
	public EvalTranslateResponse translate(@RequestHeader(value = "X-Eval-Key", required = false) String key,
			@RequestBody EvalTranslateRequest req) {
		requireKey(key);
		String translated = translationService.translateOrEnglish(req.text(), req.targetLanguage());
		return new EvalTranslateResponse(translated);
	}

	private void requireKey(String key) {
		if (evalProps.apiKey() == null || evalProps.apiKey().isBlank() || !evalProps.apiKey().equals(key)) {
			throw new ForbiddenException("Invalid eval key");
		}
	}

	public record EvalParseRequest(@NotBlank @JsonProperty("resume_text") String resumeText) {
	}

	public record EvalMatchRequest(
			String query,
			Double lat,
			Double lng,
			@JsonProperty("radius_km") Integer radiusKm,
			String category,
			Integer size) {
	}

	public record EvalMatchResponse(@JsonProperty("job_ids") List<Long> jobIds, String source) {
	}

	public record EvalTranslateRequest(
			@NotBlank String text,
			@NotBlank @JsonProperty("target_language") String targetLanguage) {
	}

	public record EvalTranslateResponse(@JsonProperty("translated_text") String translatedText) {
	}
}
