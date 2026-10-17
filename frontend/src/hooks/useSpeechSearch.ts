"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";

import { SPEECH_LANG } from "@/i18n";
import { useI18n } from "@/i18n/I18nProvider";

function recognitionCtor(): NNSpeechRecognitionConstructor | undefined {
  if (typeof window === "undefined") return undefined;
  return window.SpeechRecognition ?? window.webkitSpeechRecognition;
}

const noop = () => () => {};

/**
 * Voice search for low-literacy users (supplement G5): the browser's speech recognizer in hi-IN or
 * en-IN, following the UI language. `supported` is false where the API is missing (e.g. Firefox), and
 * callers hide the mic button. The transcript is passed to the normal search, which already handles
 * Hindi and Hinglish.
 */
export function useSpeechSearch(onTranscript: (text: string) => void) {
  const { locale } = useI18n();
  const supported = useSyncExternalStore(noop, () => recognitionCtor() !== undefined, () => false);
  const [listening, setListening] = useState(false);
  const [error, setError] = useState(false);
  const recognition = useRef<NNSpeechRecognition | null>(null);
  const callback = useRef(onTranscript);

  useEffect(() => {
    callback.current = onTranscript;
  }, [onTranscript]);

  useEffect(() => () => recognition.current?.abort(), []);

  const start = useCallback(() => {
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

  const stop = useCallback(() => {
    recognition.current?.stop();
    setListening(false);
  }, []);

  return { supported, listening, error, start, stop };
}
