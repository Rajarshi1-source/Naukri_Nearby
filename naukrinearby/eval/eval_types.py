"""Shared types and metric helpers for the resume-extraction eval."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Expected:
    skills: list[str] = field(default_factory=list)
    city: Optional[str] = None
    total_experience_months: Optional[int] = None


@dataclass
class Case:
    id: str
    resume_text: str
    expected: Expected


def _norm(s: str) -> str:
    return s.strip().lower()


def skill_prf(predicted: list[str], expected: list[str]) -> tuple[float, float, float]:
    """Precision, recall, F1 over skill sets (case-insensitive)."""
    pred = {_norm(s) for s in predicted or []}
    exp = {_norm(s) for s in expected or []}
    if not exp and not pred:
        return 1.0, 1.0, 1.0
    if not pred:
        return 0.0, 0.0, 0.0
    tp = len(pred & exp)
    precision = tp / len(pred) if pred else 0.0
    recall = tp / len(exp) if exp else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    return precision, recall, f1


def city_match(predicted: Optional[str], expected: Optional[str]) -> bool:
    if expected is None:
        return predicted is None
    if predicted is None:
        return False
    return _norm(predicted) == _norm(expected)


def months_abs_error(predicted: Optional[int], expected: Optional[int]) -> Optional[int]:
    if expected is None or predicted is None:
        return None
    return abs(int(predicted) - int(expected))
