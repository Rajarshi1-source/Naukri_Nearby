import { API_URL, ApiError, api } from "@/lib/api";
import { TranscriptionSchema, type Transcription } from "@/types";

export const speechService = {
  /** Bhashini ASR for voice search where the browser has no speech recognition. */
  async transcribe(wav: Blob, lang: string): Promise<Transcription> {
    const form = new FormData();
    form.append("audio", wav, "voice.wav");
    form.append("lang", lang);
    const raw = await api<unknown>("/api/speech/transcribe", { method: "POST", body: form });
    return TranscriptionSchema.parse(raw);
  },

  /**
   * A public job spoken by Bhashini TTS (cached per job version and language on the server). Returns
   * null when the server has no audio (stub provider), so the caller can say so.
   */
  async jobAudio(slug: string, lang: string): Promise<Blob | null> {
    const res = await fetch(
      `${API_URL}/api/jobs/${encodeURIComponent(slug)}/audio?lang=${encodeURIComponent(lang)}`,
    );
    if (res.status === 204) return null;
    if (!res.ok) throw new ApiError(res.status, `Audio request failed (${res.status})`);
    return res.blob();
  },
};
