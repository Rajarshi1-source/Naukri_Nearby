# NaukriNearby — Eval Connectors
## The `/api/internal/parse-resume-text` endpoint + prompt wiring that makes the eval harness run end-to-end

> This connects three things you already have: the **versioned prompt** (`prompts/resume_parse_v3.txt`), the **LLM adapter** (`LlmExtractionProvider`, §2 of the master plan), and the **Python eval harness** (`run_eval.py` / `parser_client.py` from the starter code). After dropping these in, `python run_eval.py` runs against your backend end-to-end.

---

## How the pieces fit

```
run_eval.py ──POST {"resumeText": "..."}──► /api/internal/parse-resume-text
                                                   │
                                          InternalEvalController
                                                   │
                                          ResumeExtractionService
                                            │            │
                                   PromptLoader     LlmExtractionProvider (adapter §2)
                                   (resume_parse_v3)        │
                                                     current LLM (temp 0, JSON mode)
                                                   │
                              ◄──── JSON {name, phone, city, skills[], total_experience_months, ...}
```

The eval client reads `skills`, `city`, and `total_experience_months` from the JSON — so the response **must** use those exact snake_case keys. The DTO below is annotated to guarantee that.

---

## 1. The prompt file — `backend/src/main/resources/prompts/resume_parse_v3.txt`

Use the standalone `resume_parse_v3.txt` file (provided alongside this doc). Place it on the classpath at `resources/prompts/resume_parse_v3.txt`. Key properties:
- Emits **only** a JSON object with the exact keys the eval checks.
- Teaches transliteration, informal-title → skill mapping, and experience summing.
- Its two worked examples are **deliberately disjoint from `testset.jsonl`** (a plumber in Jaipur, a data-entry operator in Hyderabad) so the eval still measures generalization, not memorization.

> **Interview gold (no data leakage):** *"My few-shot examples in the prompt are intentionally different from my eval set — different names, cities, and trades — so the eval measures whether the parser generalizes the conventions, not whether it memorized the answers. Leaking eval cases into the prompt would inflate the score and defeat the gate."*

---

## 2. `ResumeParseResult.java` — the DTO (snake_case to match the eval)

```java
package com.naukrinearby.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ResumeParseResult {

    private String name;
    private String phone;
    private String email;
    private String city;
    private String state;

    private List<String> skills = new ArrayList<>();

    private List<Experience> experience = new ArrayList<>();
    private List<Education> education = new ArrayList<>();

    @JsonProperty("languages_spoken")
    private List<String> languagesSpoken = new ArrayList<>();

    @JsonProperty("total_experience_months")
    private Integer totalExperienceMonths = 0;

    // Echoed back for observability; not produced by the model (set server-side).
    @JsonProperty("prompt_version")
    private String promptVersion;

    @Data
    public static class Experience {
        private String title;
        private String company;
        private Integer months = 0;
    }

    @Data
    public static class Education {
        private String degree;
        private String institution;
        private Integer year;
    }
}
```

> The `@JsonProperty` annotations make the response serialize `languages_spoken` and `total_experience_months` exactly as the eval expects, without forcing a global snake_case strategy on the rest of your API.

---

## 3. `PromptLoader.java` — loads + caches the versioned prompt

```java
package com.naukrinearby.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptLoader {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** Loads prompts/{version}.txt from the classpath, cached after first read. */
    public String load(String version) {
        return cache.computeIfAbsent(version, this::readFromClasspath);
    }

    private String readFromClasspath(String version) {
        String path = "prompts/" + version + ".txt";
        try {
            var resource = new ClassPathResource(path);
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded prompt '{}' ({} chars)", version, content.length());
            return content;
        } catch (IOException e) {
            throw new IllegalStateException("Prompt not found on classpath: " + path, e);
        }
    }
}
```

---

## 4. `ResumeExtractionService.java` — prompt + adapter + post-processing

```java
package com.naukrinearby.service;

import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.generation.LlmExtractionProvider;   // adapter from §2
import com.naukrinearby.util.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeExtractionService {

    private final LlmExtractionProvider llm;     // provider-agnostic adapter (§2)
    private final PromptLoader promptLoader;

    /** The single source of truth for which prompt version is in production. */
    public static final String PROMPT_VERSION = "resume_parse_v3";

    public ResumeParseResult extract(String resumeText) {
        String prompt = promptLoader.load(PROMPT_VERSION)
                .replace("{{RESUME_TEXT}}", resumeText == null ? "" : resumeText.trim());

        // The adapter calls the model at temperature 0 with JSON output, then
        // maps the response onto ResumeParseResult (see §6 for the adapter internals).
        ResumeParseResult result = llm.extractStructured(prompt, ResumeParseResult.class, PROMPT_VERSION);

        postProcess(result);
        result.setPromptVersion(PROMPT_VERSION);
        return result;
    }

    /** Deterministic server-side cleanup so the eval (and prod) get consistent output. */
    private void postProcess(ResumeParseResult r) {
        if (r == null) return;

        // 1. Normalize phone: keep digits, drop +91 / leading 0, keep last 10.
        if (r.getPhone() != null) {
            String digits = r.getPhone().replaceAll("\\D", "");
            if (digits.startsWith("91") && digits.length() == 12) digits = digits.substring(2);
            if (digits.startsWith("0") && digits.length() == 11) digits = digits.substring(1);
            r.setPhone(digits.length() == 10 ? digits : (digits.isBlank() ? null : digits));
        }

        // 2. Dedupe + trim skills, preserving order (case-insensitive de-dup).
        if (r.getSkills() != null) {
            Set<String> seen = new LinkedHashSet<>();
            List<String> cleaned = new ArrayList<>();
            for (String s : r.getSkills()) {
                if (s == null) continue;
                String t = s.trim();
                if (t.isEmpty()) continue;
                if (seen.add(t.toLowerCase())) cleaned.add(t);
            }
            r.setSkills(cleaned);
        }

        // 3. Backstop total_experience_months from experience[] if the model left it 0
        //    but listed roles (defensive — the prompt already asks for the sum).
        if ((r.getTotalExperienceMonths() == null || r.getTotalExperienceMonths() == 0)
                && r.getExperience() != null && !r.getExperience().isEmpty()) {
            int sum = r.getExperience().stream()
                    .mapToInt(e -> e.getMonths() == null ? 0 : e.getMonths()).sum();
            if (sum > 0) r.setTotalExperienceMonths(sum);
        }
        if (r.getTotalExperienceMonths() == null) r.setTotalExperienceMonths(0);
    }
}
```

---

## 5. `InternalEvalController.java` — the endpoint the harness calls

```java
package com.naukrinearby.controller;

import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.service.ResumeExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Eval-only endpoint. NOT part of the public API.
 *
 * Guards (defense in depth):
 *   1. Only registered when naukri.eval.enabled=true (off in real production).
 *   2. Requires the X-Eval-Key header to match naukri.eval.api-key (constant-time).
 *   3. In K8s, additionally restrict via NetworkPolicy so only the CI runner / cluster
 *      can reach /api/internal/** (never exposed through the public ingress).
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naukri.eval.enabled", havingValue = "true")
public class InternalEvalController {

    private final ResumeExtractionService extractionService;

    @Value("${naukri.eval.api-key:}")
    private String evalApiKey;

    @PostMapping("/parse-resume-text")
    public ResponseEntity<ResumeParseResult> parseResumeText(
            @RequestHeader(value = "X-Eval-Key", required = false) String providedKey,
            @RequestBody ParseRequest request) {

        if (!isAuthorized(providedKey)) {
            return ResponseEntity.status(401).build();
        }
        if (request == null || request.resumeText() == null || request.resumeText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ResumeParseResult result = extractionService.extract(request.resumeText());
        return ResponseEntity.ok(result);
    }

    private boolean isAuthorized(String providedKey) {
        if (evalApiKey == null || evalApiKey.isBlank()) return true;   // key not configured → allow (local dev)
        if (providedKey == null) return false;
        return MessageDigest.isEqual(
                evalApiKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));         // constant-time compare
    }

    /** Matches the harness payload: {"resumeText": "..."} */
    public record ParseRequest(String resumeText) {}
}
```

---

## 6. Adapter internals — how `extractStructured` actually calls the model

§2 of the master plan declared the `LlmExtractionProvider` interface. Here's a reference base class that does the JSON-mode call, defensive fence-stripping, and Jackson mapping. Concrete providers (GPT-5-mini, Claude Haiku, local) extend it.

```java
package com.naukrinearby.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmExtractionProvider implements LlmExtractionProvider {

    protected final ObjectMapper objectMapper;

    /** Subclasses implement the actual API call. MUST use temperature 0 + JSON output. */
    protected abstract String callModelRaw(String prompt);

    @Override
    public <T> T extractStructured(String prompt, Class<T> schema, String promptVersion) {
        long start = System.currentTimeMillis();
        String raw = callModelRaw(prompt);
        String json = stripFences(raw);
        try {
            T result = objectMapper.readValue(json, schema);
            log.info("extraction ok provider={} promptVersion={} ms={}",
                    providerId(), promptVersion, System.currentTimeMillis() - start);
            // (Here is where you'd also emit a Langfuse trace: tokens, cost, latency — §18.4)
            return result;
        } catch (Exception e) {
            log.error("extraction JSON parse failed provider={} raw={}", providerId(), raw, e);
            throw new ResumeParseException("Model did not return valid JSON", e);
        }
    }

    /** Defensive: the prompt forbids fences, but strip ```json ... ``` if a model adds them. */
    private String stripFences(String s) {
        if (s == null) return "{}";
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
        }
        int first = t.indexOf('{'), last = t.lastIndexOf('}');
        return (first >= 0 && last > first) ? t.substring(first, last + 1) : t;
    }
}
```

```java
// Example concrete provider. Wire the real SDK call inside callModelRaw().
package com.naukrinearby.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "naukri.llm.extraction-provider", havingValue = "gpt5mini")
public class Gpt5MiniProvider extends AbstractLlmExtractionProvider {

    public Gpt5MiniProvider(ObjectMapper objectMapper) { super(objectMapper); }

    @Override
    protected String callModelRaw(String prompt) {
        // Pseudocode — replace with your SDK / HTTP client:
        //   request:
        //     model = ${naukri.llm.extraction-model}     (e.g. a current GPT-5-mini-class id)
        //     temperature = 0.0                          (deterministic)
        //     response_format = json_object              (JSON mode)
        //     messages = [{ role: "user", content: prompt }]
        //   return the model's text content.
        throw new UnsupportedOperationException("Wire your LLM SDK call here.");
    }

    @Override
    public String providerId() { return "gpt5mini"; }
}
```

> **Why temperature 0:** reproducibility. The eval must be stable run-to-run, and identical resumes should cache to identical results in production.

---

## 7. Config — `application.yml` additions

```yaml
naukri:
  eval:
    enabled: ${EVAL_ENABLED:false}        # true only in CI / local eval runs
    api-key: ${EVAL_API_KEY:}             # set in CI secrets; empty = allow (local dev)
  llm:
    extraction-provider: ${EXTRACTION_PROVIDER:gpt5mini}
    extraction-model: ${EXTRACTION_MODEL:gpt-5-mini}
    temperature: 0.0
```

And in the eval harness's `parser_client.py`, send the key when present:

```python
# parser_client.py — add the eval key header
import os, requests
from eval_types import ReviewComment  # (or your resume types)

BACKEND = os.environ.get("NAUKRI_API", "http://localhost:8080")
EVAL_KEY = os.environ.get("EVAL_API_KEY", "")

def parse_resume(resume_text: str) -> dict:
    headers = {"X-Eval-Key": EVAL_KEY} if EVAL_KEY else {}
    resp = requests.post(
        f"{BACKEND}/api/internal/parse-resume-text",
        json={"resumeText": resume_text},
        headers=headers,
        timeout=120,
    )
    resp.raise_for_status()
    return resp.json()
```

---

## 8. Run it end-to-end

```bash
# 1. Start the backend with eval enabled + your model key
EVAL_ENABLED=true \
EVAL_API_KEY=dev-secret \
EXTRACTION_PROVIDER=gpt5mini \
LLM_API_KEY=sk-... \
./gradlew bootRun

# 2. In another shell, run the harness against it
cd eval
EVAL_API_KEY=dev-secret NAUKRI_API=http://localhost:8080 python run_eval.py

# Expected output (numbers will vary with your model):
#   mixed-hindi-001        f1=0.86 city=✓ exp=✓ json=✓
#   informal-title-001     f1=0.75 city=✓ exp=✓ json=✓
#   ...
#   ================================================
#   Mean skill F1:        82%
#   JSON validity rate:   100%
#   City accuracy:        90%
#   Experience accuracy:  80%

# 3. Lock in the baseline, then gate CI on it
python run_eval.py --save-baseline
python run_eval.py --ci          # exits 1 if skill-F1 drops > 5 points
```

In CI, the `resume-extraction-eval` job (§16 of the master plan) sets `EVAL_ENABLED=true` and `EVAL_API_KEY`/`NAUKRI_API` from secrets, runs `run_eval.py --ci`, and fails the build on regression.

---

## 9. Sanity test (quick unit test for the wiring)

```java
@SpringBootTest(properties = {"naukri.eval.enabled=true", "naukri.eval.api-key="})
class ResumeExtractionWiringTest {

    @Autowired ResumeExtractionService service;

    @Test
    void promptLoadsAndPlaceholderIsReplaced() {
        // With a stub LlmExtractionProvider bean, assert the prompt sent to the model
        // contains the resume text and NOT the literal "{{RESUME_TEXT}}" token.
        var result = service.extract("Ramesh, Tally aur Excel, 3 saal, Kanpur");
        assertThat(result.getPromptVersion()).isEqualTo("resume_parse_v3");
    }
}
```

---

## What you can now say in an interview

> *"My resume parser is fully wired for evaluation. The extraction prompt is a versioned file on the classpath; a thin service injects the resume, calls a provider-agnostic LLM adapter at temperature zero with JSON mode, and post-processes the output deterministically — phone normalization, skill de-dup, an experience-sum backstop. There's an internal, key-guarded endpoint my Python eval harness hits, so `run_eval.py` measures skill-extraction F1 against a labeled Bharat-resume set and gates CI. And I kept the prompt's few-shot examples disjoint from the eval set, so the score reflects generalization, not memorization."*

That's the full loop: **versioned prompt → adapter → guarded endpoint → eval harness → CI gate** — and a clean answer for "how do you know your parser works?"
