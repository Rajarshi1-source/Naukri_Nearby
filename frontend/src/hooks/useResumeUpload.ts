"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { ApiError } from "@/lib/api";
import { candidateService } from "@/services/candidateService";
import type { CandidateProfile } from "@/types";

const MAX_POLL_ATTEMPTS = 30;
const POLL_INTERVAL_MS = 2000;

/** Discriminated union describing where the upload + async parse currently is. */
export type ResumeState =
  | { phase: "idle" }
  | { phase: "uploading" }
  | { phase: "parsing"; resumeId: string }
  | { phase: "done"; profile: CandidateProfile }
  | { phase: "failed"; message: string };

/**
 * Handles resume upload then polls GET /api/resumes/{id}/parse-status via React Query until the
 * parse completes, fails, or times out — loading the candidate profile on success.
 */
export function useResumeUpload() {
  const [state, setState] = useState<ResumeState>({ phase: "idle" });
  const attemptsRef = useRef(0);

  const resumeId = state.phase === "parsing" ? state.resumeId : null;

  const statusQuery = useQuery({
    queryKey: ["resume-status", resumeId],
    queryFn: () => candidateService.parseStatus(resumeId as string),
    enabled: !!resumeId,
    refetchInterval: resumeId ? POLL_INTERVAL_MS : false,
    gcTime: 0,
  });

  // Reacts to each poll result from React Query (an external system) and advances the parse
  // state machine; setState-in-effect is the intended pattern for syncing external data here.
  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!resumeId || !statusQuery.data) return;
    attemptsRef.current += 1;
    const status = statusQuery.data.status;
    if (status === "COMPLETED") {
      void candidateService.getProfile().then(
        (profile) => setState({ phase: "done", profile }),
        () =>
          setState({
            phase: "failed",
            message: "Parsed, but your profile could not be loaded.",
          }),
      );
    } else if (status === "FAILED") {
      setState({
        phase: "failed",
        message:
          "We couldn't read that resume. You can edit your profile manually.",
      });
    } else if (attemptsRef.current > MAX_POLL_ATTEMPTS) {
      setState({
        phase: "failed",
        message: "Parsing is taking longer than expected. Check back shortly.",
      });
    }
    // statusQuery.dataUpdatedAt advances on every poll, even when status is unchanged.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resumeId, statusQuery.dataUpdatedAt]);

  useEffect(() => {
    if (resumeId && statusQuery.isError) {
      setState({ phase: "failed", message: "Lost connection while parsing." });
    }
  }, [resumeId, statusQuery.isError]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const upload = useCallback(async (file: File) => {
    attemptsRef.current = 0;
    setState({ phase: "uploading" });
    try {
      const res = await candidateService.uploadResume(file);
      setState({ phase: "parsing", resumeId: res.resumeId });
    } catch (err) {
      setState({
        phase: "failed",
        message: err instanceof ApiError ? err.message : "Upload failed",
      });
    }
  }, []);

  return { state, upload };
}
