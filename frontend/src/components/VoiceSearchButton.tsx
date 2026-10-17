"use client";

import { Mic, MicOff } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useSpeechSearch } from "@/hooks/useSpeechSearch";
import { useT } from "@/i18n/I18nProvider";
import { cn } from "@/lib/utils";

/** Mic button for voice search; hidden where the browser has no speech recognition. */
export default function VoiceSearchButton({ onTranscript }: { onTranscript: (text: string) => void }) {
  const t = useT();
  const { supported, listening, error, start, stop } = useSpeechSearch(onTranscript);
  if (!supported) return null;
  return (
    <div className="flex items-center gap-2">
      <Button
        type="button"
        size="icon"
        variant={listening ? "default" : "outline"}
        aria-pressed={listening}
        aria-label={listening ? t.search.voiceStop : t.search.voiceStart}
        title={listening ? t.search.voiceStop : t.search.voiceStart}
        onClick={listening ? stop : start}
        className={cn("size-9 shrink-0", listening && "animate-pulse")}
      >
        {listening ? <MicOff className="size-4" /> : <Mic className="size-4" />}
      </Button>
      <span role="status" className="sr-only">
        {listening ? t.search.listening : error ? t.search.voiceError : ""}
      </span>
    </div>
  );
}
