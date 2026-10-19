"use client";

import { useCallback, useEffect, useRef, useState, useSyncExternalStore } from "react";
import { Loader2, Square, Volume2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useT } from "@/i18n/I18nProvider";
import { speechService } from "@/services/speechService";

/** Matches the server's naukri.speech.max-spoken-chars, so both paths read the same amount. */
const MAX_SPOKEN_CHARS = 700;

type Status = "idle" | "loading" | "playing" | "unavailable";

function hasSynthesis(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}

function subscribeVoices(onChange: () => void) {
  if (!hasSynthesis()) return () => {};
  window.speechSynthesis.addEventListener("voiceschanged", onChange);
  return () => window.speechSynthesis.removeEventListener("voiceschanged", onChange);
}

/** A string snapshot (voices load asynchronously in Chrome), so the store value is stable. */
function voiceLangs(): string {
  return hasSynthesis() ? window.speechSynthesis.getVoices().map((v) => v.lang).join(",") : "";
}

function findVoice(lang: string): SpeechSynthesisVoice | undefined {
  if (!hasSynthesis()) return undefined;
  const voices = window.speechSynthesis.getVoices();
  const norm = (v: SpeechSynthesisVoice) => v.lang.toLowerCase().replace("_", "-");
  return (
    voices.find((v) => norm(v) === `${lang}-in`) ?? voices.find((v) => norm(v).split("-")[0] === lang)
  );
}

/**
 * "Listen" for low-literacy users (ADR 0009). Speaks `text` (already in `textLang`) with the browser's
 * own voice when it has one for that language, which costs nothing and works offline. Otherwise it plays
 * the server's Bhashini audio for `lang`, translated there and cached per job version.
 */
export default function ListenButton({
  slug,
  lang,
  text,
  textLang,
}: {
  slug: string;
  /** The language the reader asked for; the server translates to it. */
  lang: string;
  text: string;
  textLang: string;
}) {
  const t = useT();
  const [status, setStatus] = useState<Status>("idle");
  const audio = useRef<HTMLAudioElement | null>(null);
  const audioUrl = useRef<string | null>(null);
  // Subscribing keeps the browser's voice list fresh; findVoice reads it at click time.
  useSyncExternalStore(subscribeVoices, voiceLangs, () => "");

  const stopAll = useCallback(() => {
    if (hasSynthesis()) window.speechSynthesis.cancel();
    audio.current?.pause();
  }, []);

  const releaseAudio = useCallback(() => {
    audio.current?.pause();
    audio.current = null;
    if (audioUrl.current) URL.revokeObjectURL(audioUrl.current);
    audioUrl.current = null;
  }, []);

  // The parent keys this component by job and language, so unmount is the only cleanup needed.
  useEffect(
    () => () => {
      stopAll();
      releaseAudio();
    },
    [stopAll, releaseAudio],
  );

  const playServer = useCallback(async () => {
    setStatus("loading");
    try {
      if (!audio.current) {
        const blob = await speechService.jobAudio(slug, lang);
        if (!blob) {
          setStatus("unavailable");
          return;
        }
        audioUrl.current = URL.createObjectURL(blob);
        audio.current = new Audio(audioUrl.current);
      }
      const el = audio.current;
      el.currentTime = 0;
      el.onended = () => setStatus("idle");
      el.onerror = () => setStatus("unavailable");
      await el.play();
      setStatus("playing");
    } catch {
      setStatus("unavailable");
    }
  }, [slug, lang]);

  const play = useCallback(() => {
    const voice = text.trim() ? findVoice(textLang) : undefined;
    if (!voice) {
      void playServer();
      return;
    }
    const utterance = new SpeechSynthesisUtterance(text.slice(0, MAX_SPOKEN_CHARS));
    utterance.voice = voice;
    utterance.lang = voice.lang;
    utterance.rate = 0.9; // a little slower for clarity
    utterance.onend = () => setStatus("idle");
    utterance.onerror = (event) => {
      // "interrupted"/"canceled" come from our own stop; anything else falls back to the server.
      if (event.error === "interrupted" || event.error === "canceled") setStatus("idle");
      else void playServer();
    };
    window.speechSynthesis.cancel();
    window.speechSynthesis.speak(utterance);
    setStatus("playing");
  }, [text, textLang, playServer]);

  const stop = useCallback(() => {
    stopAll();
    setStatus("idle");
  }, [stopAll]);

  const playing = status === "playing";
  return (
    <span className="inline-flex items-center gap-2">
      <Button
        type="button"
        size="sm"
        variant={playing ? "default" : "outline"}
        aria-pressed={playing}
        disabled={status === "loading"}
        onClick={playing ? stop : play}
        className="h-8 gap-1.5"
      >
        {status === "loading" ? (
          <Loader2 className="size-4 animate-spin" aria-hidden />
        ) : playing ? (
          <Square className="size-3.5" aria-hidden />
        ) : (
          <Volume2 className="size-4" aria-hidden />
        )}
        {playing ? t.job.listenStop : t.job.listen}
      </Button>
      <span role="status" className="text-xs text-muted-foreground">
        {status === "loading" ? t.job.listenLoading : status === "unavailable" ? t.job.listenUnavailable : ""}
      </span>
    </span>
  );
}
