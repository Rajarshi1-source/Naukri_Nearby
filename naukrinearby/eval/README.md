# Resume-Extraction Eval Harness

Exercises the **exact production extraction path** (prompt `resume_parse_v3` + LLM adapter +
deterministic post-processing) through the backend's eval-only endpoint.

## Prerequisites
The backend must be running with the eval endpoint enabled:

```bash
EVAL_ENABLED=true EVAL_API_KEY=dev-eval-key \
LLM_API_KEY=sk-... EXTRACTION_PROVIDER=gpt5mini \
./gradlew bootRun
```

(Set `EXTRACTION_PROVIDER=stub` to run with no API key — lower accuracy, useful for smoke tests.)

## Run

```bash
cd eval
pip install -r requirements.txt
export NAUKRI_BASE_URL=http://localhost:8080
export EVAL_API_KEY=dev-eval-key

python run_eval.py                 # report
python run_eval.py --ci            # gate against baseline.json (exit 1 on regression)
python run_eval.py --update-baseline
```

## Metrics
- `skill_f1` — set F1 over extracted vs expected skills (primary signal)
- `city_accuracy` — exact (case-insensitive) city match rate
- `experience_mae` — mean absolute error of `total_experience_months`

## Match-quality eval (recall@10, MRR)
Scores the production search/match ranking (hybrid BM25+pgvector when `SEARCH_RANKING=hybrid`).
Seed the jobs referenced in `match_testset.jsonl` via the job-create API, fill in their `relevant_ids`,
then:

```bash
python match_eval.py
```

- `recall_at_10` — fraction of relevant jobs found in the top 10
- `mrr` — mean reciprocal rank of the first relevant job

## Translation-quality eval
Spot-checks English→regional alert phrasing via `POST /api/internal/eval/translate`. With the stub
provider the source is echoed (non-English cases MISS); run with `TRANSLATION_PROVIDER=bhashini` for a
real signal:

```bash
python translation_eval.py
```

## Trace logging (Langfuse-style)
Set `EVAL_TRACE_ENABLED=true` to log one structured line per extraction (`provider`, `promptVersion`,
`latencyMs`, `jsonValid`). Off by default; ship these to Langfuse/Loki in production.
