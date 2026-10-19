package com.naukrinearby.generation;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.naukrinearby.config.SpeechProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bhashini ASR and TTS through the ULCA pipeline API ({@link BhashiniPipelineClient} does the config +
 * compute exchange). Payloads per bhashini.gitbook.io/bhashini-apis (pipeline compute request/response):
 * <ul>
 * <li><b>ASR</b> — {@code config:{language:{sourceLanguage}, serviceId, audioFormat:"wav",
 * samplingRate:16000}}, {@code inputData.audio[{audioContent:<base64>}]}; the transcript is
 * {@code pipelineResponse[0].output[0].source}.</li>
 * <li><b>TTS</b> — {@code config:{language:{sourceLanguage}, serviceId, gender}},
 * {@code inputData.input[{source}]}; base64 WAV is {@code pipelineResponse[0].audio[0].audioContent}.</li>
 * </ul>
 * Retries and the {@code bhashini} circuit breaker wrap each call. Audio and transcripts are never logged.
 * Selected by {@code naukri.speech.provider=bhashini}.
 */
@Component
@ConditionalOnProperty(name = "naukri.speech.provider", havingValue = "bhashini")
public class BhashiniSpeechProvider implements SpeechToTextProvider, TextToSpeechProvider {

	static final int ASR_SAMPLING_RATE = 16_000;

	private final BhashiniPipelineClient pipeline;
	private final String gender;

	@Autowired
	public BhashiniSpeechProvider(SpeechProperties props) {
		this(props, Clock.systemUTC());
	}

	BhashiniSpeechProvider(SpeechProperties props, Clock clock) {
		if (BhashiniPipelineClient.isBlank(props.userId()) || BhashiniPipelineClient.isBlank(props.ulcaApiKey())
				|| BhashiniPipelineClient.isBlank(props.pipelineId())) {
			throw new IllegalStateException("naukri.speech.provider=bhashini needs BHASHINI_USER_ID, "
					+ "BHASHINI_ULCA_API_KEY and BHASHINI_PIPELINE_ID");
		}
		this.pipeline = new BhashiniPipelineClient(props.configUrl(), props.userId(), props.ulcaApiKey(),
				props.pipelineId(), props.timeout(), props.configTtl(), clock);
		this.gender = props.ttsGender();
	}

	@Override
	@Retry(name = "bhashini")
	@CircuitBreaker(name = "bhashini")
	public String transcribe(byte[] wav, String languageCode) {
		var response = pipeline.compute("asr",
				Map.of("sourceLanguage", languageCode),
				Map.of("audioFormat", "wav", "samplingRate", ASR_SAMPLING_RATE),
				Map.of("audio", List.of(Map.of("audioContent", Base64.getEncoder().encodeToString(wav)))));
		var output = response.first().firstOutput();
		return output == null || output.source() == null ? "" : output.source().strip();
	}

	@Override
	@Retry(name = "bhashini")
	@CircuitBreaker(name = "bhashini")
	public byte[] synthesize(String text, String languageCode) {
		if (text == null || text.isBlank()) {
			return new byte[0];
		}
		var response = pipeline.compute("tts",
				Map.of("sourceLanguage", languageCode),
				Map.of("gender", gender),
				Map.of("input", List.of(Map.of("source", text))));
		String audio = response.first().firstAudioContent();
		if (audio == null || audio.isBlank()) {
			throw new IllegalStateException("Bhashini returned no audio");
		}
		return Base64.getMimeDecoder().decode(audio);
	}

	@Override
	public String providerId() {
		return "bhashini";
	}
}
