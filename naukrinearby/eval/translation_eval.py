#!/usr/bin/env python3
"""Translation-quality eval: spot-checks English->regional job-alert phrasing.

Each case in translation_testset.jsonl has an English source, a target language code, and a list of
expected substrings (keywords) that a correct translation should contain. With the stub translation
provider the source is echoed unchanged (so non-English cases will MISS); point the backend at a real
Bhashini-compatible provider (TRANSLATION_PROVIDER=bhashini) to measure real quality.

Usage:
    python translation_eval.py
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from parser_client import ParserClient

HERE = Path(__file__).parent
TESTSET = HERE / "translation_testset.jsonl"


def load_cases() -> list[dict]:
    if not TESTSET.exists():
        return []
    cases = []
    for line in TESTSET.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line:
            cases.append(json.loads(line))
    return cases


def run() -> dict:
    client = ParserClient()
    cases = load_cases()
    if not cases:
        print("No translation_testset.jsonl cases — skipping translation eval.")
        return {"keyword_accuracy": 0.0, "n": 0}

    hits = 0
    print(f"\nRunning {len(cases)} translation cases...\n")
    for case in cases:
        result = client.translate(case["text"], case["target_language"])
        out = result.get("translated_text", "") or ""
        expected = case.get("expected_contains", [])
        ok = all(kw in out for kw in expected) if expected else bool(out)
        hits += 1 if ok else 0
        print(f"  [{case.get('id')}] lang={case['target_language']} {'OK' if ok else 'MISS'}: {out[:60]}")

    metrics = {
        "keyword_accuracy": round(hits / len(cases), 4),
        "n": len(cases),
    }
    print("\n=== Translation Aggregate ===")
    for k, v in metrics.items():
        print(f"  {k}: {v}")
    return metrics


if __name__ == "__main__":
    run()
    sys.exit(0)
