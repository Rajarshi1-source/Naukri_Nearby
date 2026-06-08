"""Client for the eval-only extraction endpoint exposed by the Spring Boot backend.

It hits the EXACT production extraction path (same prompt + adapter + post-processing) via
POST /api/internal/eval/parse, authenticated with the X-Eval-Key header (eval connectors §5).
"""
from __future__ import annotations

import os

import requests


class ParserClient:
    def __init__(self, base_url: str | None = None, eval_key: str | None = None, timeout: int = 90):
        self.base_url = (base_url or os.environ.get("NAUKRI_BASE_URL", "http://localhost:8080")).rstrip("/")
        self.eval_key = eval_key or os.environ.get("EVAL_API_KEY", "")
        self.timeout = timeout

    def parse(self, resume_text: str) -> dict:
        resp = requests.post(
            f"{self.base_url}/api/internal/eval/parse",
            json={"resume_text": resume_text},
            headers={"X-Eval-Key": self.eval_key, "Content-Type": "application/json"},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()
