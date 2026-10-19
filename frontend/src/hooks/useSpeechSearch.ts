"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

import { SPEECH_LANG } from "@/i18n";
import { useI18n } from "@/i18n/I18nProvider";
import { MAX_RECORDING_SECONDS, canRecordAudio, toWav16kMono } from "@/lib/wav";
import { speechService } from "@/services/speechService";

function recognitionCtor(): NNSpeechRecognitionConstructor | undefined {
  if (typeof window === "undefined") return undefined;
  return window.SpeechRecognition ?? window.webkitSpeechRecognition;
}

/** "browser" = Web Speech API; "server" = record here, transcribe with Bhashini; "none" = hide the mic. */
export type SpeechMode = "browser" | "server" | "none";

function detectMode(): SpeechMode {
  if (recognitionCtor()) return "browser";
  return canRecordAudio() ? "server" : "none";
}

const noop = () => () => {};

/**
 * Voice search for low-literacy users (supplement G5, ADR 0009). Uses the browser's speech recognizer
 * in hi-IN or en-IN, following the UI language. Where that API is missing (Firefox, many Android
 * WebViews) it records with MediaRecorder instead, converts the clip to 16 kHz mono WAV in the browser
 * and has the backend transcribe it with Bhashini. Recording stops by itself at the server's 30 s cap.
 * The transcript is passed to the normal search, which already handles Hindi and Hinglish.
 */
export function useSpeechSearch(onTranscript: (text: string) => void) {
  const { locale } = useI18n();
  const mode = useSyncExternalStore<SpeechMode>(noop, detectMode, () => "none");
  const [listening, setListening] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [error, setError] = useState(false);
  const recognition = useRef<NNSpeechRecognition | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const autoStop = useRef<ReturnType<typeof setTimeout> | null>(null);
  const callback = useRef(onTranscript);

  useEffect(() => {
    callback.current = onTranscript;
  }, [onTranscript]);

  useEffect(
    () => () => {
      recognition.current?.abort();
      if (autoStop.current) clearTimeout(autoStop.current);
      const r = recorder.current;
      if (r) {
        r.onstop = null;
        if (r.state !== "inactive") r.stop();
        r.stream.getTracks().forEach((track) => track.stop());
      }
    },
    [],
  );

  const startBrowser = useCallback(() => {
    const Ctor = recognitionCtor();
    if (!Ctor) return;
    recognition.current?.abort();
    const r = new Ctor();
    r.lang = SPEECH_LANG[locale];
    r.continuous = false;
    r.interimResults = false;
    r.maxAlternatives = 1;
    r.onresult = (event) => {
      const result = event.results[event.resultIndex];
      const text = result?.[0]?.transcript?.trim();
      if (text) callback.current(text);
    };
    r.onerror = () => setError(true);
    r.onend = () => setListening(false);
    recognition.current = r;
    setError(false);
    setListening(true);
    try {
      r.start();
    } catch {
      setListening(false);
      setError(true);
    }
  }, [locale]);

  const startRecording = useCallback(async () => {
    setError(false);
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1 } });
    } catch {
      setError(true); // permission denied or no microphone
      return;
    }
    const chunks: Blob[] = [];
    const r = new MediaRecorder(stream);
    r.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    r.onstop = async () => {
      stream.getTracks().forEach((track) => track.stop());
      if (autoStop.current) clearTimeout(autoStop.current);
      recorder.current = null;
      setListening(false);
      if (chunks.length === 0) return;
      setTranscribing(true);
      try {
        const wav = await toWav16kMono(new Blob(chunks, { type: r.mimeType }));
        const { text } = await speechService.transcribe(wav, locale);
        if (text.trim()) callback.current(text.trim());
        else setError(true);
      } catch {
        setError(true);
      } finally {
        setTranscribing(false);
      }
    };
    recorder.current = r;
    r.start();
    setListening(true);
    // Leave headroom under the server cap for encoder padding.
    autoStop.current = setTimeout(() => {
      if (r.state !== "inactive") r.stop();
    }, (MAX_RECORDING_SECONDS - 1) * 1000);
  }, [locale]);

  const start = useCallback(() => {
    if (mode === "browser") startBrowser();
    else if (mode === "server") void startRecording();
  }, [mode, startBrowser, startRecording]);

  const stop = useCallback(() => {
    if (recorder.current && recorder.current.state !== "inactive") {
      recorder.current.stop(); // onstop clears listening and starts transcription
      return;
    }
    recognition.current?.stop();
    setListening(false);
  }, []);

  return { supported: mode !== "none", mode, listening, transcribing, error, start, stop };
}
