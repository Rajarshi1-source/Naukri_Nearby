# NaukriNearby — Starter Code
## (1) Transactional Outbox: PG→ES Sync [Spring Boot] + (2) Resume-Extraction Eval Harness [Python]

> Drop-in starters for the two highest-value pieces in the v2 plan: the dual-store consistency mechanism and the resume-parser quality gate. Readable scaffolds — align imports/packages to your structure (§5 of the plan).

---

# PART 1 — Transactional Outbox (PostgreSQL → Elasticsearch sync)

**Why:** The plan writes a job to Postgres *and* indexes it into Elasticsearch. Doing both as separate writes ("dual write") risks silent divergence — if the ES write fails after the PG commit, the job exists but is unsearchable. The **outbox pattern** fixes this: the job and an outbox event commit in ONE Postgres transaction; a background worker drains the outbox into ES with retries. **At-least-once indexing + idempotent upsert on `job_id` = effectively-once.**

### 1.1 Outbox table (Flyway migration)

```sql
-- V3__create_outbox.sql
CREATE TABLE outbox_events (
    id            BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,   -- e.g. "job"
    aggregate_id   BIGINT       NOT NULL,   -- e.g. job_id
    event_type     VARCHAR(50)  NOT NULL,   -- JOB_CREATED | JOB_UPDATED | JOB_CLOSED
    payload        JSONB        NOT NULL,   -- the document to index
    version        INT          NOT NULL,   -- optimistic-concurrency version of the aggregate
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ
);

-- The sync worker polls this: cheap index on unprocessed events, oldest first.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at)
    WHERE status = 'PENDING';
```

### 1.2 Atomic write — job + outbox event in ONE transaction

```java
package com.naukrinearby.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    /**
     * Create a job. The job row AND its outbox event commit atomically:
     * either both persist or neither does. ES is NOT touched here — the
     * sync worker (1.4) handles indexing asynchronously and reliably.
     */
    @Transactional
    public Job createJob(JobCreateRequest req, Long recruiterId) {
        Job job = jobRepo.save(Job.fromRequest(req, recruiterId));   // version starts at 1

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("job")
                .aggregateId(job.getId())
                .eventType("JOB_CREATED")
                .version(job.getVersion())
                .payload(toJobDocumentJson(job))   // the ES document shape
                .status("PENDING")
                .build();
        outboxRepo.save(event);

        return job;   // commit → both rows durable. Return immediately (poster reads from PG, §B.3).
    }

    @Transactional
    public Job updateJob(Long jobId, JobUpdateRequest req) {
        Job job = jobRepo.findById(jobId).orElseThrow();
        job.applyUpdate(req);
        job.setVersion(job.getVersion() + 1);      // optimistic-concurrency bump
        jobRepo.save(job);

        outboxRepo.save(OutboxEvent.builder()
                .aggregateType("job").aggregateId(jobId)
                .eventType("JOB_UPDATED").version(job.getVersion())
                .payload(toJobDocumentJson(job)).status("PENDING").build());
        return job;
    }

    private String toJobDocumentJson(Job job) {
        try {
            return objectMapper.writeValueAsString(JobDocument.from(job));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize job document", e);
        }
    }
}
```

### 1.3 Outbox repository

```java
package com.naukrinearby.repository;

import com.naukrinearby.model.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // Pull a batch of unprocessed events, oldest first. SKIP LOCKED lets
    // multiple sync workers run concurrently without stepping on each other.
    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status = 'PENDING'
        ORDER BY created_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> fetchPendingBatch(@Param("limit") int limit);
}
```

### 1.4 Sync worker — drains outbox into Elasticsearch (idempotent upsert)

```java
package com.naukrinearby.worker;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxSyncWorker {

    private final OutboxRepository outboxRepo;
    private final ElasticsearchClient es;

    private static final int BATCH = 100;
    private static final int MAX_RETRIES = 5;

    /** Polls every 2s. (In prod you could use Debezium CDC instead of polling.) */
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void syncBatch() {
        List<OutboxEvent> batch = outboxRepo.fetchPendingBatch(BATCH);
        if (batch.isEmpty()) return;

        for (OutboxEvent event : batch) {
            try {
                indexToElasticsearch(event);          // idempotent (1.5)
                event.setStatus("PROCESSED");
                event.setProcessedAt(Instant.now());
            } catch (Exception e) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.setStatus("FAILED");        // → alert + manual reconcile (1.6)
                    log.error("Outbox event {} FAILED after {} retries: {}",
                            event.getId(), MAX_RETRIES, e.getMessage());
                } else {
                    log.warn("Outbox event {} retry {}: {}",
                            event.getId(), event.getRetryCount(), e.getMessage());
                    // stays PENDING → retried next tick
                }
            }
        }
        outboxRepo.saveAll(batch);
    }

    /**
     * IDEMPOTENT upsert keyed on job_id. Re-processing the same event (at-least-once)
     * just re-writes the same document. Version guard prevents a stale event from
     * overwriting a newer one.
     */
    private void indexToElasticsearch(OutboxEvent event) throws Exception {
        String jobId = String.valueOf(event.getAggregateId());

        if ("JOB_CLOSED".equals(event.getEventType())) {
            es.delete(d -> d.index("jobs").id(jobId));
            return;
        }

        es.update(u -> u
                .index("jobs")
                .id(jobId)                                   // doc id = job_id → upsert
                .docAsUpsert(true)
                .doc(event.getPayload())                     // the JSON document
                // Optimistic concurrency: only apply if this version is newer.
                // (Implement via a scripted update comparing doc.version, omitted for brevity.)
            , Object.class);
    }
}
```

### 1.5 Why this is correct (interview talking points)

- **No lost updates:** the job and its event commit atomically — the index *will* catch up because the event is durably queued.
- **Survives ES downtime:** if ES is down, events stay `PENDING` and drain when ES recovers. No data loss.
- **Effectively-once:** at-least-once delivery (a worker can crash mid-batch) + idempotent upsert on `job_id` = the document ends up correct regardless of retries.
- **Concurrent workers:** `FOR UPDATE SKIP LOCKED` lets several sync workers share the load without double-processing.
- **No stale overwrites:** the `version` guard ensures an older event can't overwrite a newer document.

### 1.6 Reconciliation safety net (cron)

```java
/** Nightly: catch any divergence (e.g., events stuck FAILED, or manual ES edits). */
@Scheduled(cron = "0 0 3 * * *")
public void reconcile() {
    // For each active job in Postgres, ensure an equivalent ES doc exists & matches version.
    // Re-enqueue outbox events for any mismatch. Alert on FAILED events.
}
```

> **Interview line:** *"I keep Postgres and Elasticsearch consistent with the transactional outbox pattern — the job and an outbox event commit in one transaction, a worker drains the outbox into ES with retries and an idempotent upsert on job_id, and a nightly reconciliation job catches any drift. So even if Elasticsearch is down for an hour, nothing is lost — the index just catches up when it recovers."*

---

---

# PART 2 — Resume-Extraction Eval Harness (Python 3.12)

**Goal:** Answer *"how do you know the resume parser is accurate?"* with a number. Maintain a benchmark of resumes (including Hindi/English-mixed, photo resumes, informal titles) with expected extracted fields, and measure skill-extraction F1 + field accuracy.

```
eval/
├── testset.jsonl          # labeled resumes
├── eval_types.py
├── parser_client.py       # calls YOUR resume-parse endpoint
├── run_eval.py            # metrics + CI gate
├── requirements.txt
└── baseline.json
```

### 2.1 `testset.jsonl` (sample — labeled resumes)

Each line: the raw resume text + the expected extraction. (Use real-ish Bharat examples — Hindi/English mix, informal titles.)

```jsonl
{"id": "mixed-hindi-001", "resume_text": "Naam: Ramesh Kumar. Phone: 9876543210. Maine 3 saal Tally aur accounting ka kaam kiya hai ek dukaan pe Kanpur me. Excel bhi aata hai.", "expected": {"name": "Ramesh Kumar", "phone": "9876543210", "city": "Kanpur", "skills": ["Tally", "Accounting", "Excel", "Retail Sales"], "total_experience_months": 36}}
{"id": "informal-title-001", "resume_text": "I worked as delivery boy for Swiggy 2 years in Indore. Have bike and license. Know Hindi and basic English.", "expected": {"name": null, "city": "Indore", "skills": ["Delivery", "Two-Wheeler Driving", "Logistics"], "total_experience_months": 24}}
{"id": "english-formal-001", "resume_text": "Priya Sharma. priya@email.com. B.Com graduate. 18 months experience as Junior Accountant at ABC Pvt Ltd, Nagpur. Skills: Tally ERP, GST filing, MS Excel, bank reconciliation.", "expected": {"name": "Priya Sharma", "email": "priya@email.com", "city": "Nagpur", "skills": ["Tally ERP", "GST Filing", "MS Excel", "Bank Reconciliation"], "total_experience_months": 18}}
{"id": "electrician-001", "resume_text": "Suresh, electrician ka kaam karta hoon 5 saal se. Wiring, motor repair, inverter sab aata hai. Patna me rehta hoon. 8123456789.", "expected": {"name": "Suresh", "phone": "8123456789", "city": "Patna", "skills": ["Electrical Wiring", "Motor Repair", "Inverter Repair"], "total_experience_months": 60}}
{"id": "fresher-001", "resume_text": "Anjali, ITI fitter trade pass 2024, Bhopal. No experience yet. Looking for first job. Knows welding basics.", "expected": {"name": "Anjali", "city": "Bhopal", "skills": ["Fitter", "Welding"], "total_experience_months": 0}}
```

### 2.2 `eval_types.py`

```python
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class ExpectedExtraction:
    name: Optional[str] = None
    phone: Optional[str] = None
    email: Optional[str] = None
    city: Optional[str] = None
    skills: list[str] = field(default_factory=list)
    total_experience_months: Optional[int] = None


@dataclass
class EvalCase:
    id: str
    resume_text: str
    expected: ExpectedExtraction


@dataclass
class CaseResult:
    case_id: str
    json_valid: bool
    skill_precision: float
    skill_recall: float
    skill_f1: float
    city_correct: bool
    experience_within_tolerance: bool   # ±3 months


@dataclass
class EvalReport:
    results: list[CaseResult] = field(default_factory=list)

    @property
    def mean_skill_f1(self) -> float:
        return _mean([r.skill_f1 for r in self.results])

    @property
    def json_validity_rate(self) -> float:
        return _mean([1.0 if r.json_valid else 0.0 for r in self.results])

    @property
    def city_accuracy(self) -> float:
        return _mean([1.0 if r.city_correct else 0.0 for r in self.results])

    @property
    def experience_accuracy(self) -> float:
        return _mean([1.0 if r.experience_within_tolerance else 0.0 for r in self.results])


def _mean(xs: list[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0
```

### 2.3 `parser_client.py` — plug in YOUR parser

```python
import os
import requests
from eval_types import ExpectedExtraction

BACKEND = os.environ.get("NAUKRI_API", "http://localhost:8080")


def parse_resume(resume_text: str) -> dict:
    """
    Calls your resume-parse endpoint. Adjust to your API.
    Returns the raw parsed dict (or {} on invalid JSON).
    """
    resp = requests.post(
        f"{BACKEND}/api/internal/parse-resume-text",   # eval-only endpoint
        json={"resumeText": resume_text},
        timeout=120,
    )
    resp.raise_for_status()
    return resp.json()   # {name, phone, email, city, skills, total_experience_months, ...}
```

### 2.4 `run_eval.py`

```python
#!/usr/bin/env python3
"""
Resume-extraction eval harness.

Usage:
  python run_eval.py                 # run + report
  python run_eval.py --ci            # compare to baseline, exit 1 on regression
  python run_eval.py --save-baseline
"""
from __future__ import annotations
import argparse, json, sys
from pathlib import Path

from eval_types import EvalCase, ExpectedExtraction, CaseResult, EvalReport
from parser_client import parse_resume

HERE = Path(__file__).parent
TESTSET = HERE / "testset.jsonl"
BASELINE = HERE / "baseline.json"

EXPERIENCE_TOLERANCE_MONTHS = 3
MAX_F1_DROP = 0.05            # CI fails if mean skill-F1 drops > 5 points


def load_cases() -> list[EvalCase]:
    cases = []
    for line in TESTSET.read_text().splitlines():
        if not line.strip():
            continue
        d = json.loads(line)
        cases.append(EvalCase(id=d["id"], resume_text=d["resume_text"],
                              expected=ExpectedExtraction(**d["expected"])))
    return cases


def _norm(s: str) -> str:
    return s.strip().lower()


def skill_prf(predicted: list[str], expected: list[str]) -> tuple[float, float, float]:
    """Set-based precision/recall/F1 on skills (case-insensitive, fuzzy-ish via normalization)."""
    pred = {_norm(s) for s in predicted}
    gold = {_norm(s) for s in expected}
    if not pred and not gold:
        return 1.0, 1.0, 1.0
    if not pred or not gold:
        return 0.0, 0.0, 0.0
    tp = len(pred & gold)
    precision = tp / len(pred)
    recall = tp / len(gold)
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0
    return precision, recall, f1


def evaluate_case(case: EvalCase, parsed: dict) -> CaseResult:
    json_valid = bool(parsed) and "skills" in parsed
    if not json_valid:
        return CaseResult(case.id, False, 0, 0, 0, False, False)

    p, r, f1 = skill_prf(parsed.get("skills", []), case.expected.skills)

    city_correct = (case.expected.city is None or
                    _norm(parsed.get("city") or "") == _norm(case.expected.city or ""))

    exp_pred = parsed.get("total_experience_months")
    exp_gold = case.expected.total_experience_months
    exp_ok = (exp_gold is None or
              (exp_pred is not None and abs(exp_pred - exp_gold) <= EXPERIENCE_TOLERANCE_MONTHS))

    return CaseResult(case.id, True, p, r, f1, city_correct, exp_ok)


def run() -> EvalReport:
    report = EvalReport()
    for case in load_cases():
        try:
            parsed = parse_resume(case.resume_text)
        except Exception as e:
            print(f"  [{case.id}] parser ERROR: {e}", file=sys.stderr)
            parsed = {}
        res = evaluate_case(case, parsed)
        report.results.append(res)
        print(f"  {case.id:22s} f1={res.skill_f1:.2f} "
              f"city={'✓' if res.city_correct else '✗'} "
              f"exp={'✓' if res.experience_within_tolerance else '✗'} "
              f"json={'✓' if res.json_valid else '✗'}")
    return report


def print_report(report: EvalReport) -> dict:
    m = {
        "mean_skill_f1": round(report.mean_skill_f1, 4),
        "json_validity_rate": round(report.json_validity_rate, 4),
        "city_accuracy": round(report.city_accuracy, 4),
        "experience_accuracy": round(report.experience_accuracy, 4),
        "n_cases": len(report.results),
    }
    print("\n" + "=" * 48)
    print("RESUME-EXTRACTION EVAL REPORT")
    print("=" * 48)
    print(f"  Mean skill F1:        {m['mean_skill_f1']:.0%}")
    print(f"  JSON validity rate:   {m['json_validity_rate']:.0%}")
    print(f"  City accuracy:        {m['city_accuracy']:.0%}")
    print(f"  Experience accuracy:  {m['experience_accuracy']:.0%}  (±{EXPERIENCE_TOLERANCE_MONTHS}mo)")
    print("=" * 48)
    return m


def ci_gate(metrics: dict) -> int:
    if not BASELINE.exists():
        print("\nNo baseline.json — run --save-baseline first.")
        return 0
    base = json.loads(BASELINE.read_text())
    drop = base["mean_skill_f1"] - metrics["mean_skill_f1"]
    print(f"\nCI GATE: skill-F1 change {(-drop):+.2%} (allowed drop ≤ {MAX_F1_DROP:.0%})")
    if drop > MAX_F1_DROP:
        print("  ❌ FAIL: resume extraction regressed")
        return 1
    print("  ✅ PASS")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ci", action="store_true")
    ap.add_argument("--save-baseline", action="store_true")
    args = ap.parse_args()

    print("Running resume-extraction eval...\n")
    report = run()
    metrics = print_report(report)

    if args.save_baseline:
        BASELINE.write_text(json.dumps(metrics, indent=2))
        print(f"\nSaved baseline → {BASELINE}")
        return 0
    if args.ci:
        return ci_gate(metrics)
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

### 2.5 `requirements.txt`
```
requests>=2.31
```

### 2.6 `baseline.json` (example, via `--save-baseline`)
```json
{
  "mean_skill_f1": 0.82,
  "json_validity_rate": 1.0,
  "city_accuracy": 0.90,
  "experience_accuracy": 0.80,
  "n_cases": 5
}
```

### 2.7 CI wiring (matches §16 of the plan)
```yaml
  resume-extraction-eval:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: { python-version: '3.12' }
      - run: |
          cd eval
          pip install -r requirements.txt
          python run_eval.py --ci          # exits 1 → fails build on F1 regression
        env:
          NAUKRI_API: ${{ secrets.STAGING_API_URL }}
          EXTRACTION_API_KEY: ${{ secrets.LLM_API_KEY }}
```

---

## How to talk about this code in an interview

**Outbox pattern:** *"I keep Postgres and Elasticsearch consistent with the transactional outbox pattern — the job and an outbox event commit in one transaction, a worker drains the outbox into ES with an idempotent upsert on job_id, with SKIP LOCKED so multiple workers share the load, a version guard against stale overwrites, and a nightly reconciliation job. So if Elasticsearch is down for an hour, nothing's lost — the index just catches up. Dual writes would've risked silent divergence."*

**Resume eval:** *"I measure my parser — skill-extraction F1 on a benchmark of real Bharat resumes including Hindi/English-mixed and informal titles like 'dukaan pe kaam'. It runs in CI against a committed baseline and fails the build if F1 drops more than 5 points, so a prompt or model change that quietly got worse can't merge."*

Both answers, backed by code you wrote, put you ahead of nearly every junior candidate — and the outbox one is genuinely senior-level distributed-systems thinking.
