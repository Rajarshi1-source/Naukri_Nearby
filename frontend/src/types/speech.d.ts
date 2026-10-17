/**
 * Minimal Web Speech API recognition types (not yet in TypeScript's DOM lib). Only what
 * useSpeechSearch uses; Chrome/Edge/Android expose it as webkitSpeechRecognition.
 */
interface NNSpeechRecognitionAlternative {
  readonly transcript: string;
  readonly confidence: number;
}

interface NNSpeechRecognitionResult {
  readonly isFinal: boolean;
  readonly length: number;
  [index: number]: NNSpeechRecognitionAlternative;
}

interface NNSpeechRecognitionEvent extends Event {
  readonly resultIndex: number;
  readonly results: {
    readonly length: number;
    [index: number]: NNSpeechRecognitionResult;
  };
}

interface NNSpeechRecognition extends EventTarget {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  start(): void;
  stop(): void;
  abort(): void;
  onresult: ((event: NNSpeechRecognitionEvent) => void) | null;
  onerror: ((event: Event) => void) | null;
  onend: (() => void) | null;
}

type NNSpeechRecognitionConstructor = new () => NNSpeechRecognition;

interface Window {
  SpeechRecognition?: NNSpeechRecognitionConstructor;
  webkitSpeechRecognition?: NNSpeechRecognitionConstructor;
}
