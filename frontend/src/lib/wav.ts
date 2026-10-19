/**
 * Browser-side audio for the Bhashini voice-search fallback: decode whatever MediaRecorder produced
 * (webm/opus, ogg/opus, mp4/aac), resample to 16 kHz mono with an OfflineAudioContext, and write a
 * 16-bit PCM WAV, which is the one format the backend accepts. No dependency; Web Audio does the work.
 */

export const ASR_SAMPLE_RATE = 16_000;
/** Matches the backend cap (naukri.speech.max-audio-seconds). */
export const MAX_RECORDING_SECONDS = 30;

export async function toWav16kMono(recording: Blob): Promise<Blob> {
  const ctx = new AudioContext();
  let decoded: AudioBuffer;
  try {
    decoded = await ctx.decodeAudioData(await recording.arrayBuffer());
  } finally {
    void ctx.close();
  }
  const seconds = Math.min(decoded.duration, MAX_RECORDING_SECONDS);
  const frames = Math.max(1, Math.floor(seconds * ASR_SAMPLE_RATE));
  // One output channel: the destination down-mixes stereo input to mono.
  const offline = new OfflineAudioContext(1, frames, ASR_SAMPLE_RATE);
  const source = offline.createBufferSource();
  source.buffer = decoded;
  source.connect(offline.destination);
  source.start();
  const rendered = await offline.startRendering();
  return encodeWav(rendered.getChannelData(0), ASR_SAMPLE_RATE);
}

/** 16-bit little-endian PCM WAV with a canonical 44-byte header. */
export function encodeWav(samples: Float32Array, sampleRate: number): Blob {
  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  const ascii = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++) view.setUint8(offset + i, text.charCodeAt(i));
  };
  ascii(0, "RIFF");
  view.setUint32(4, 36 + samples.length * 2, true);
  ascii(8, "WAVE");
  ascii(12, "fmt ");
  view.setUint32(16, 16, true); // fmt chunk size
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, 1, true); // mono
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 2, true); // byte rate
  view.setUint16(32, 2, true); // block align
  view.setUint16(34, 16, true); // bits per sample
  ascii(36, "data");
  view.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    view.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buffer], { type: "audio/wav" });
}

/** True where the recorder fallback can work: MediaRecorder, a microphone API, and Web Audio. */
export function canRecordAudio(): boolean {
  return (
    typeof window !== "undefined" &&
    typeof window.MediaRecorder !== "undefined" &&
    typeof window.OfflineAudioContext !== "undefined" &&
    typeof window.AudioContext !== "undefined" &&
    typeof navigator !== "undefined" &&
    typeof navigator.mediaDevices?.getUserMedia === "function"
  );
}
