"use client";

import { Loader2, Mic, MicOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useSpeechSearch } from "@/hooks/useSpeechSearch";
import { useT } from "@/i18n/I18nProvider";
import { cn } from "@/lib/utils";

/**
 * Mic button for voice search. Uses the browser's recognizer where it exists, else records and asks
 * the server (Bhashini ASR); hidden only where neither is possible.
 */
export default function VoiceSearchButton({ onTranscript }: { onTranscript: (text: string) => void }) {
  const t = useT();
  const { supported, mode, listening, transcribing, error, start, stop } = useSpeechSearch(onTranscript);
  if (!supported) return null;
  const label = listening ? t.search.voiceStop : t.search.voiceStart;
  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        size="icon"
        variant={listening ? "default" : "outline"}
        aria-pressed={listening}
        aria-label={label}
        title={label}
        data-voice-mode={mode}
        disabled={transcribing}
        onClick={listening ? stop : start}
        className={cn("size-9 shrink-0", listening && "animate-pulse")}
      >
        {transcribing ? (
          <Loader2 className="size-4 animate-spin" />
        ) : listening ? (
          <MicOff className="size-4" />
        ) : (
          <Mic className="size-4" />
        )}
      </Button>
      <span role="status" className="sr-only">
        {listening ? t.search.listening : transcribing ? t.search.transcribing : error ? t.search.voiceError : ""}
      </span>
    </div>
  );
}
