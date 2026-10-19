package com.naukrinearby.generation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.config.SpeechProperties;
import com.naukrinearby.util.WavFixtures;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bhashini ASR and TTS over the real ULCA pipeline exchange (config call, then compute call) against a
 * local fake Bhashini server. Payload shapes follow bhashini.gitbook.io/bhashini-apis (pipeline
 * config/compute request + response pages).
 */
class BhashiniSpeechProviderTest {

	private static final byte[] TTS_WAV = WavFixtures.silence(22_050, 1, 16, 0.05);

	private HttpServer server;
	private final List<Recorded> requests = new CopyOnWriteArrayList<>();
	private final AtomicInteger computeStatus = new AtomicInteger(200);

	record Recorded(String path, String userId, String ulcaApiKey, String authorization, String body) {
	}

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		server.createContext("/ulca/apis/v0/model/getModelsPipeline", ex -> {
			String body = record(ex);
			String task = body.contains("\"asr\"") ? """
					{"taskType":"asr","config":[
					  {"serviceId":"ai4bharat/conformer-en-gpu--t4","modelId":"m1","language":{"sourceLanguage":"en"},"domain":["general"]},
					  {"serviceId":"ai4bharat/conformer-hi-gpu--t4","modelId":"m2","language":{"sourceLanguage":"hi"},"domain":["general"]}]}
					""" : """
					{"taskType":"tts","config":[
					  {"serviceId":"ai4bharat/indic-tts-coqui-misc-gpu--t4","modelId":"m3","language":{"sourceLanguage":"en"},"supportedVoices":["male","female"]},
					  {"serviceId":"ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4","modelId":"m4","language":{"sourceLanguage":"hi"},"supportedVoices":["male","female"]}]}
					""";
			respond(ex, 200, """
					{"languages":[],"pipelineResponseConfig":[%s],
					 "pipelineInferenceAPIEndPoint":{"callbackUrl":"%s/services/inference/pipeline",
					   "inferenceApiKey":{"name":"Authorization","value":"inference-key-123"},
					   "isMultilingualEnabled":true,"isSyncApi":true}}
					""".formatted(task, base));
		});
		server.createContext("/services/inference/pipeline", ex -> {
			String body = record(ex);
			if (computeStatus.get() != 200) {
				respond(ex, computeStatus.get(), "{}");
				return;
			}
			if (body.contains("\"asr\"")) {
				respond(ex, 200, """
						{"pipelineResponse":[{"taskType":"asr",
						  "config":{"serviceId":"ai4bharat/conformer-hi-gpu--t4",
						    "language":{"sourceLanguage":"hi","sourceScriptCode":""},
						    "audioFormat":"wav","encoding":null,"samplingRate":16000,"postProcessors":null},
						  "output":[{"source":" डिलीवरी की नौकरी "}],"audio":null}]}
						""");
			}
			else {
				respond(ex, 200, """
						{"pipelineResponse":[{"taskType":"tts",
						  "config":{"language":{"sourceLanguage":"hi","sourceScriptCode":""},"audioFormat":"wav",
						    "encoding":"base64","samplingRate":22050,"postProcessors":null},
						  "output":null,"audio":[{"audioContent":"%s","audioUri":null}]}]}
						""".formatted(Base64.getEncoder().encodeToString(TTS_WAV)));
			}
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void asr_configThenCompute_withTheDocumentedHeadersAndPayloads() {
		BhashiniSpeechProvider provider = new BhashiniSpeechProvider(props());
		byte[] clip = WavFixtures.voiceSearchClip(0.2);

		assertThat(provider.transcribe(clip, "hi")).isEqualTo("डिलीवरी की नौकरी");

		assertThat(requests).hasSize(2);
		Recorded config = requests.get(0);
		assertThat(config.path()).isEqualTo("/ulca/apis/v0/model/getModelsPipeline");
		assertThat(config.userId()).isEqualTo("user-1");
		assertThat(config.ulcaApiKey()).isEqualTo("ulca-key-1");
		assertThat(config.body()).contains("\"taskType\":\"asr\"", "\"sourceLanguage\":\"hi\"",
				"\"pipelineId\":\"pipeline-1\"");

		Recorded compute = requests.get(1);
		assertThat(compute.path()).isEqualTo("/services/inference/pipeline");
		assertThat(compute.authorization()).isEqualTo("inference-key-123");
		assertThat(compute.body()).contains("\"taskType\":\"asr\"",
				"\"serviceId\":\"ai4bharat/conformer-hi-gpu--t4\"", // the hi model, not the first one listed
				"\"audioFormat\":\"wav\"", "\"samplingRate\":16000",
				"\"audioContent\":\"" + Base64.getEncoder().encodeToString(clip) + "\"");
	}

	@Test
	void tts_configThenCompute_decodesTheBase64Wav() {
		BhashiniSpeechProvider provider = new BhashiniSpeechProvider(props());

		assertThat(provider.synthesize("डिलीवरी पार्टनर", "hi")).isEqualTo(TTS_WAV);

		assertThat(requests.get(0).body()).contains("\"taskType\":\"tts\"", "\"sourceLanguage\":\"hi\"");
		Recorded compute = requests.get(1);
		assertThat(compute.authorization()).isEqualTo("inference-key-123");
		assertThat(compute.body()).contains("\"taskType\":\"tts\"",
				"\"serviceId\":\"ai4bharat/indic-tts-coqui-indo_aryan-gpu--t4\"", "\"gender\":\"female\"",
				"\"source\":\"डिलीवरी पार्टनर\"");
	}

	@Test
	void pipelineConfig_isCachedPerTaskAndLanguage() {
		BhashiniSpeechProvider provider = new BhashiniSpeechProvider(props());
		provider.transcribe(WavFixtures.voiceSearchClip(0.1), "hi");
		provider.transcribe(WavFixtures.voiceSearchClip(0.1), "hi");
		provider.synthesize("Delivery Partner", "hi");
		provider.transcribe(WavFixtures.voiceSearchClip(0.1), "en");

		// asr:hi, tts:hi, asr:en — the second asr:hi reuses the cached config
		assertThat(requests).extracting(Recorded::path).filteredOn(p -> p.endsWith("getModelsPipeline")).hasSize(3);
	}

	@Test
	void rejectedInferenceKey_dropsTheCachedConfig() {
		BhashiniSpeechProvider provider = new BhashiniSpeechProvider(props());
		provider.synthesize("Delivery Partner", "hi");
		computeStatus.set(403);
		assertThatThrownBy(() -> provider.synthesize("Delivery Partner", "hi")).isInstanceOf(RuntimeException.class);
		computeStatus.set(200);

		provider.synthesize("Delivery Partner", "hi");
		assertThat(requests).extracting(Recorded::path).filteredOn(p -> p.endsWith("getModelsPipeline")).hasSize(2);
	}

	@Test
	void blankText_isNotSentToTts() {
		assertThat(new BhashiniSpeechProvider(props()).synthesize("  ", "hi")).isEmpty();
		assertThat(requests).isEmpty();
	}

	@Test
	void missingCredentials_failFast() {
		assertThatThrownBy(() -> new BhashiniSpeechProvider(new SpeechProperties("bhashini", null, "u", "k", "",
				null, null, null, null, null, null, null, null, null, null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("BHASHINI_PIPELINE_ID");
	}

	private SpeechProperties props() {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		return new SpeechProperties("bhashini", base + "/ulca/apis/v0/model/getModelsPipeline",
				"user-1", "ulca-key-1", "pipeline-1", Duration.ofSeconds(5), Duration.ofHours(1), null,
				null, null, null, null, null, null, null);
	}

	private String record(HttpExchange ex) throws IOException {
		String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		requests.add(new Recorded(ex.getRequestURI().getPath(),
				ex.getRequestHeaders().getFirst("userID"),
				ex.getRequestHeaders().getFirst("ulcaApiKey"),
				ex.getRequestHeaders().getFirst("Authorization"),
				body));
		return body;
	}

	private static void respond(HttpExchange ex, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		ex.sendResponseHeaders(status, bytes.length);
		ex.getResponseBody().write(bytes);
		ex.close();
	}
}
