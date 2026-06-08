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

    def _post(self, path: str, payload: dict) -> dict:
        resp = requests.post(
            f"{self.base_url}{path}",
            json=payload,
            headers={"X-Eval-Key": self.eval_key, "Content-Type": "application/json"},
            timeout=self.timeout,
        )
        resp.raise_for_status()
        return resp.json()

    def parse(self, resume_text: str) -> dict:
        return self._post("/api/internal/eval/parse", {"resume_text": resume_text})

    def match(self, query: dict) -> dict:
        """Returns {"job_ids": [...], "source": "..."} for a search/match query."""
        return self._post("/api/internal/eval/match", query)

    def translate(self, text: str, target_language: str) -> dict:
        """Returns {"translated_text": "..."} for an English source string."""
        return self._post(
            "/api/internal/eval/translate",
            {"text": text, "target_language": target_language},
        )
