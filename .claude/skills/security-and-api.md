# NaukriNearby — Security & API Reference

Detailed reference for the Spring Boot 4.0.6 / Spring Framework 7 backend, loaded on demand for any
task touching **authentication, authorization, secrets, PII handling, or the REST contract**. The
main `SKILL.md` carries the day-to-day rules; this file carries the depth. All examples are Java 21,
Spring Security 7, stateless JWT.

---

## Part A — Security

### A.1 Authentication model: phone + OTP → JWT (passwordless)

NaukriNearby has **no passwords**. Identity is a verified Indian mobile number; sessions are
stateless JWTs. The flow:

```
1. POST /api/auth/send-otp { phone }
   → normalize phone → rate-limit check (Redis) → Twilio Verify sends OTP
   → store nothing secret server-side beyond the Twilio Verify SID / a Redis attempt counter
2. POST /api/auth/verify-otp { phone, code }
   → Twilio Verify check → on success, find-or-create User → issue access + refresh JWT
3. Client sends `Authorization: Bearer <accessToken>` on every protected call
4. POST /api/auth/refresh { refreshToken } → new access token
```

Use **Twilio Verify** for OTP issuance/checking rather than generating and storing OTPs yourself —
it handles delivery, expiry, and retry limits, and keeps the secret off your server. If you do
generate OTPs locally, store only a hash in Redis with a short TTL (see A.4), never the plaintext.

### A.2 JWT issuance, validation, refresh

- **Algorithm:** prefer asymmetric **RS256** (sign with a private key, verify with the public key)
  so verification can be distributed later without sharing the signing key. HS256 is acceptable for
  a single-service MVP but commit to RS256 if you mention "scaling out" in interviews.
- **Access token:** short-lived (≈15 min). Claims: `sub` (user id), `role` (`CANDIDATE`/`EMPLOYER`),
  `phone_verified`, `iat`, `exp`, `jti`. Keep claims minimal — no PII beyond what's needed.
- **Refresh token:** longer-lived (≈7–30 days), opaque or JWT, **stored server-side** (Redis) keyed
  by `jti` so it can be revoked (logout, theft). Rotate on every refresh (issue a new refresh token,
  invalidate the old `jti`).
- **Validation:** verify signature, `exp`, and issuer/audience on every request in a
  `OncePerRequestFilter`. Reject early with 401 — never let an unverified token reach a controller.
- **Logout:** delete the refresh `jti` from Redis and (optionally) add the access `jti` to a
  short-TTL Redis denylist until it expires.

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtDecoder jwtDecoder;          // Spring Security RS256 decoder
    private final UserDetailsLookup users;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) { chain.doFilter(req, res); return; }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring(7));   // verifies signature + exp
            var auth = users.toAuthentication(jwt);             // builds principal + authorities
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            res.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

### A.3 Stateless SecurityFilterChain (Spring Security 7, lambda DSL)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity        // enables @PreAuthorize for role checks
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())               // safe ONLY because sessions are stateless + JWT in header, not cookies
            .cors(Customizer.withDefaults())            // see A.7
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/api/webhooks/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/jobs/**").permitAll()   // public job pages (SEO)
                .requestMatchers("/api/employer/**").hasRole("EMPLOYER")
                .requestMatchers("/api/candidate/**").hasRole("CANDIDATE")
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                    res.sendError(HttpStatus.UNAUTHORIZED.value()))
                .accessDeniedHandler((req, res, ex) ->
                    res.sendError(HttpStatus.FORBIDDEN.value())));
        return http.build();
    }
}
```

> CSRF note: disabling CSRF is correct **only** because the API is stateless and the token travels
> in the `Authorization` header (not a cookie). If you ever store the JWT in a cookie, re-enable CSRF
> protection. Say this explicitly in interviews — "I disabled CSRF because there's no cookie-based
> session to forge."

Method-level guard where path rules aren't enough (e.g. a candidate may only read their own profile):

```java
@PreAuthorize("#userId == authentication.principal.id")
public CandidateProfile getProfile(Long userId) { ... }
```

### A.4 Rate limiting (Redis)

Two limits matter for abuse/cost control:

| Limit | Key | Window | Action on breach |
|---|---|---|---|
| OTP requests | `otp:attempts:{phone}` | 5 per hour | 429 + `ProblemDetail` "Too many OTP requests" |
| API requests | `rate:{userId or ip}` | 100 per minute | 429 |
| WhatsApp sends | `notify:daily:{candidateId}` | 3 per day | skip send (not an error) |

Use Redis `INCR` + `EXPIRE` on first hit (a simple fixed/sliding window). Apply the API limit in a
filter or an `@ConcurrencyLimit`-adjacent interceptor; apply the OTP limit inside `AuthService`
before calling Twilio so you never pay for abusive traffic.

```java
boolean allowOtp(String phone) {
    String key = "otp:attempts:" + phone;
    Long n = redis.opsForValue().increment(key);
    if (n != null && n == 1L) redis.expire(key, Duration.ofHours(1));
    return n != null && n <= 5;
}
```

### A.5 Twilio webhook signature verification (delivery-status callbacks)

`POST /api/webhooks/twilio` receives delivery status. **Never trust it unverified** — anyone can POST
to a public URL. Validate the `X-Twilio-Signature` header (HMAC-SHA1 of the full URL + sorted POST
params, keyed by your Twilio auth token) before processing.

```java
@PostMapping("/api/webhooks/twilio")
ResponseEntity<Void> twilioStatus(HttpServletRequest req,
                                  @RequestParam MultiValueMap<String, String> params) {
    String signature = req.getHeader("X-Twilio-Signature");
    String url = props.publicWebhookUrl();   // the exact public URL Twilio called
    var validator = new RequestValidator(props.authToken());
    Map<String, String> flat = params.toSingleValueMap();
    if (signature == null || !validator.validate(url, flat, signature)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    // also dedup on MessageSid (Redis SETNX) — Twilio may retry the same callback
    notificationService.recordDeliveryStatus(flat.get("MessageSid"), flat.get("MessageStatus"));
    return ResponseEntity.ok().build();
}
```

The webhook path is `permitAll()` in the filter chain (Twilio has no JWT) — the signature check
**is** its authentication. Keep it idempotent: dedup on `MessageSid`.

### A.6 Secrets & configuration

- All credentials (LLM API key, Twilio SID/token, Bhashini key, JWT keys, DB password, MinIO keys)
  come from environment variables via `@ConfigurationProperties` records — never hardcoded, never in
  `application.yml` committed to git. The repo ships an `.env.example` with empty placeholders only.
- The JWT signing key (RS256 private key) is an injected secret, not a checked-in file. In
  Kubernetes it's a `Secret`, surfaced through `envFrom: secretRef` (matches plan §14.3).
- Rotate Twilio/LLM keys without a redeploy by reading them from the environment at startup and
  documenting the rotation runbook.

### A.7 CORS

The Next.js frontend is a separate origin. Allow it explicitly — never `*` with credentials.

```java
@Bean
CorsConfigurationSource corsConfigurationSource(SecurityProperties props) {
    var cfg = new CorsConfiguration();
    cfg.setAllowedOrigins(props.allowedOrigins());     // e.g. https://naukrinearby.in, http://localhost:3000
    cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    cfg.setAllowedHeaders(List.of("Authorization","Content-Type"));
    cfg.setMaxAge(Duration.ofHours(1));
    var src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/api/**", cfg);
    return src;
}
```

### A.8 PII & DPDP Act handling (resumes are sensitive)

Resumes contain names, phones, addresses — personal data under India's DPDP Act (§13 of the plan).

- **Encrypt resume files at rest** in MinIO/S3 (server-side encryption); serve them only via
  **short-lived signed URLs**, never public links.
- **Redact PII in logs:** never log full phone/email/resume text. Mask to `98XXXXXX10`. Add a log
  filter or a dedicated masking util; keep raw PII out of Langfuse traces (log skill tags, not the
  raw resume).
- **Data residency:** keep Postgres/ES/Redis/MinIO in an India region (plan's data zones).
- **Right to erasure:** `ON DELETE CASCADE` on `user_id` FKs (already in the schema) plus a job that
  purges the resume file from MinIO when a user is deleted.
- **Least data in tokens:** JWT claims hold `sub`/`role`, not name/email/phone.

---

## Part B — REST API contract

### B.1 Endpoint catalogue (§7.3)

```
AUTH
  POST   /api/auth/send-otp            { phone }                       → 200 (always, to avoid enumeration)
  POST   /api/auth/verify-otp          { phone, code }                 → 200 { accessToken, refreshToken, role }
  POST   /api/auth/refresh             { refreshToken }                → 200 { accessToken }
  GET    /api/auth/me                                                  → 200 { id, role, phone, name }
  POST   /api/auth/logout                                              → 204

JOBS (employer)
  POST   /api/jobs                     JobCreateRequest                → 201 JobResponse  (writes outbox event)
  PUT    /api/jobs/{id}                JobUpdateRequest                → 200 JobResponse
  PATCH  /api/jobs/{id}/status         { status }                      → 200
  GET    /api/employer/jobs                                           → 200 Page<JobResponse>

SEARCH (public)
  GET    /api/jobs/search?lat&lng&radius&q&category&sort&page          → 200 SearchResponse
  GET    /api/jobs/{slug}                                              → 200 JobResponse (SSR/SEO)
  GET    /api/jobs/recommended                                        → 200 List<MatchResult>  (auth)

CANDIDATE
  GET    /api/candidate/profile                                       → 200 CandidateProfileResponse
  PUT    /api/candidate/profile        ProfileUpdateRequest            → 200
  POST   /api/resumes/upload           multipart file                 → 202 { resumeId }  (async parse)
  GET    /api/resumes/{id}/parse-status                               → 200 { status, profile? }

APPLICATIONS
  POST   /api/applications             { jobId, coverNote? }           → 201  (idempotent on (job,candidate))
  GET    /api/candidate/applications                                  → 200 Page<ApplicationResponse>
  GET    /api/employer/jobs/{id}/applicants                           → 200 Page<ApplicantResponse>
  PATCH  /api/applications/{id}/status { status }                     → 200

NOTIFICATIONS
  GET    /api/notifications/preferences                               → 200
  PUT    /api/notifications/preferences NotificationPrefsRequest       → 200
  POST   /api/webhooks/twilio          (Twilio-signed)                → 200 / 403

DASHBOARD
  GET    /api/candidate/dashboard/stats                               → 200 DashboardStatsDTO
  GET    /api/employer/dashboard/stats                                → 200 DashboardStatsDTO
```

### B.2 Error shape — ProblemDetail (RFC 9457)

Every error returns `application/problem+json` via the single `@RestControllerAdvice`
(`GlobalExceptionHandler` in `SKILL.md`). Standard shape:

```json
{
  "type": "https://naukrinearby.in/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "skills must not be empty",
  "instance": "/api/jobs",
  "errors": [{ "field": "skills", "message": "must not be empty" }]
}
```

Status-code conventions:

| Code | When |
|---|---|
| 400 | Bean Validation failure (`MethodArgumentNotValidException`) |
| 401 | Missing/invalid/expired JWT |
| 403 | Authenticated but wrong role; failed Twilio signature |
| 404 | Slug/id not found |
| 409 | Duplicate application; optimistic-lock version conflict |
| 422 | Resume parse produced unusable output |
| 429 | OTP/API rate limit exceeded |
| 503 | Downstream open circuit (LLM/Twilio/Bhashini) after fallback exhausted |

### B.3 Conventions

- **Auth header:** `Authorization: Bearer <accessToken>` on every protected endpoint. Public job
  reads need none.
- **Pagination:** `?page=0&size=20&sort=createdAt,desc`; responses are Spring `Page<T>` (content +
  `totalElements` + `totalPages` + `number`). Cap `size` (e.g. ≤ 50) to prevent heavy queries.
- **Versioning:** prefer Framework 7 API versioning (e.g. an `X-API-Version` header or a version
  predicate) over scattering `/v1/` across controllers. Keep one strategy.
- **Read-your-writes:** the employer "my jobs" view reads from Postgres (source of truth), not ES,
  so a just-posted job is visible before the outbox sync indexes it (plan §9.5).
- **Idempotency:** `POST /api/applications` is idempotent on the `UNIQUE(job_id, candidate_id)`
  constraint — a duplicate returns 409 (or 200 with the existing application), never a second row.
  The Twilio webhook dedups on `MessageSid`.
- **Async upload:** resume upload returns `202 Accepted` with a `resumeId`; the client polls
  `parse-status` (the LLM parse runs on a virtual thread / queue). Don't block the upload request on
  the LLM call.

### B.4 Key DTOs (records)

```java
public record JobCreateRequest(
        @NotBlank String title, @NotBlank String description,
        @NotNull JobCategory category, List<String> skillsRequired,
        Integer salaryMin, Integer salaryMax,
        @NotNull Double lat, @NotNull Double lng,
        @NotBlank String city, @NotBlank String state, String pincode,
        Integer radiusKm) {}

public record JobResponse(Long id, String title, String slug, String city,
                          String companyName, Integer salaryMin, Integer salaryMax,
                          JobStatus status, Instant createdAt) {
    public static JobResponse from(Job j) { /* map */ }
}

public record SearchResponse(List<JobHit> hits, long total, boolean fellBackToPostgis) {
    public record JobHit(JobResponse job, double distanceKm, double score) {}
}
```

Expose `fellBackToPostgis` so the frontend can show "showing nearby results (search degraded)" when
Elasticsearch is down and the PostGIS fallback served the query (plan §9.6).

### B.5 Validation

Validate at the controller boundary with `@Valid` on request records; Bean Validation annotations
(`@NotBlank`, `@NotNull`, `@Size`, `@Pattern` for phone) live on the record components. Also validate
geo input in the service: reject `lat`/`lng` outside India's bounding box and clamp `radiusKm` to a
sane max (plan §13 mitigations).

---

## Cross-references

- Filter chain, `ProblemDetail` handler skeleton, `@ConfigurationProperties` pattern, virtual
  threads → `SKILL.md`.
- Outbox + idempotent ES sync, PostGIS fallback → `SKILL.md` + plan §9 and the starter code.
- Language-level concerns (records, sealed results, pattern matching) → `naukrinearby-java21`.
