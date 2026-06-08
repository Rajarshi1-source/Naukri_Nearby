#!/usr/bin/env python3
"""Match-quality eval: recall@10 and MRR for hyperlocal job search/match.

Each case in match_testset.jsonl is a query plus the set of job ids that are *relevant* for it.
The harness calls the backend's match endpoint (which runs the production search path, including
hybrid BM25+pgvector when enabled) and scores the returned ranking.

Prereqs: backend running with EVAL_ENABLED=true and the catalogue of jobs in match_testset.jsonl
already indexed (seed them via the normal job-create API, then point relevant_ids at their ids).

Usage:
    python match_eval.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from parser_client import ParserClient

HERE = Path(__file__).parent
TESTSET = HERE / "match_testset.jsonl"
K = 10


def load_cases() -> list[dict]:
    if not TESTSET.exists():
        return []
    cases = []
    for line in TESTSET.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            cases.append(json.loads(line))
    return cases


def recall_at_k(ranked: list[int], relevant: set[int], k: int) -> float:
    if not relevant:
        return 1.0
    top = set(ranked[:k])
    return len(top & relevant) / len(relevant)


def reciprocal_rank(ranked: list[int], relevant: set[int]) -> float:
    for i, job_id in enumerate(ranked, start=1):
        if job_id in relevant:
            return 1.0 / i
    return 0.0


def run() -> dict:
    client = ParserClient()
    cases = load_cases()
    if not cases:
        print("No match_testset.jsonl cases — skipping match eval.")
        return {"recall_at_10": 0.0, "mrr": 0.0, "n": 0}

    recalls, rrs = [], []
    print(f"\nRunning {len(cases)} match cases...\n")
    for case in cases:
        result = client.match(case["query"])
        ranked = [int(x) for x in result.get("job_ids", [])]
        relevant = {int(x) for x in case.get("relevant_ids", [])}
        r = recall_at_k(ranked, relevant, K)
        rr = reciprocal_rank(ranked, relevant)
        recalls.append(r)
        rrs.append(rr)
        print(f"  [{case.get('id')}] recall@{K}={r:.2f} rr={rr:.2f} source={result.get('source')}")

    metrics = {
        "recall_at_10": round(sum(recalls) / len(recalls), 4),
        "mrr": round(sum(rrs) / len(rrs), 4),
        "n": len(cases),
    }
    print("\n=== Match Aggregate ===")
    for k, v in metrics.items():
        print(f"  {k}: {v}")
    return metrics


if __name__ == "__main__":
    run()
    sys.exit(0)
