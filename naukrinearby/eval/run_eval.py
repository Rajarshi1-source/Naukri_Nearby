#!/usr/bin/env python3
"""Resume-extraction eval harness (starter Part 2 + connectors).

Runs every case in testset.jsonl through the backend's eval endpoint, computes skill F1, city
accuracy, and experience MAE, prints a report, and (with --ci) gates against baseline.json.

Usage:
    python run_eval.py                 # run + print report
    python run_eval.py --ci            # also fail (exit 1) on regression vs baseline.json
    python run_eval.py --update-baseline
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from eval_types import Case, Expected, city_match, months_abs_error, skill_prf
from parser_client import ParserClient

HERE = Path(__file__).parent
TESTSET = HERE / "testset.jsonl"
BASELINE = HERE / "baseline.json"

# Allowed regression before --ci fails the build.
F1_TOLERANCE = 0.03
CITY_TOLERANCE = 0.05
MAE_TOLERANCE = 2.0


def load_cases() -> list[Case]:
    cases = []
    for line in TESTSET.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        obj = json.loads(line)
        exp = obj.get("expected", {})
        cases.append(Case(
            id=obj["id"],
            resume_text=obj["resume_text"],
            expected=Expected(
                skills=exp.get("skills", []),
                city=exp.get("city"),
                total_experience_months=exp.get("total_experience_months"),
            ),
        ))
    return cases


def run() -> dict:
    client = ParserClient()
    cases = load_cases()

    f1s, city_hits, maes = [], 0, []
    print(f"\nRunning {len(cases)} resume-extraction cases...\n")
    for case in cases:
        result = client.parse(case.resume_text)
        p, r, f1 = skill_prf(result.get("skills", []), case.expected.skills)
        f1s.append(f1)
        ok_city = city_match(result.get("city"), case.expected.city)
        city_hits += 1 if ok_city else 0
        mae = months_abs_error(result.get("total_experience_months"), case.expected.total_experience_months)
        if mae is not None:
            maes.append(mae)
        print(f"  [{case.id}] skillF1={f1:.2f} city={'OK' if ok_city else 'MISS'} "
              f"expMAE={mae if mae is not None else '-'}")

    metrics = {
        "skill_f1": round(sum(f1s) / len(f1s), 4) if f1s else 0.0,
        "city_accuracy": round(city_hits / len(cases), 4) if cases else 0.0,
        "experience_mae": round(sum(maes) / len(maes), 4) if maes else 0.0,
        "n": len(cases),
    }
    print("\n=== Aggregate ===")
    for k, v in metrics.items():
        print(f"  {k}: {v}")
    return metrics


def gate(metrics: dict) -> int:
    if not BASELINE.exists():
        print("\nNo baseline.json — skipping gate. Create one with --update-baseline.")
        return 0
    base = json.loads(BASELINE.read_text(encoding="utf-8"))
    failures = []
    if metrics["skill_f1"] < base["skill_f1"] - F1_TOLERANCE:
        failures.append(f"skill_f1 {metrics['skill_f1']} < {base['skill_f1']} - {F1_TOLERANCE}")
    if metrics["city_accuracy"] < base["city_accuracy"] - CITY_TOLERANCE:
        failures.append(f"city_accuracy {metrics['city_accuracy']} < {base['city_accuracy']} - {CITY_TOLERANCE}")
    if metrics["experience_mae"] > base["experience_mae"] + MAE_TOLERANCE:
        failures.append(f"experience_mae {metrics['experience_mae']} > {base['experience_mae']} + {MAE_TOLERANCE}")
    if failures:
        print("\nREGRESSION DETECTED:")
        for f in failures:
            print(f"  - {f}")
        return 1
    print("\nEval gate PASSED (no regression vs baseline).")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ci", action="store_true", help="fail on regression vs baseline.json")
    ap.add_argument("--update-baseline", action="store_true", help="write current metrics to baseline.json")
    args = ap.parse_args()

    metrics = run()

    if args.update_baseline:
        BASELINE.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        print(f"\nWrote baseline -> {BASELINE}")
        return 0
    if args.ci:
        return gate(metrics)
    return 0


if __name__ == "__main__":
    sys.exit(main())
