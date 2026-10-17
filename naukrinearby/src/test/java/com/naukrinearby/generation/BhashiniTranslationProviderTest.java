package com.naukrinearby.generation;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.config.TranslationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real ULCA pipeline exchange (config call, then compute call) against a local fake
 * Bhashini server, so headers, JSON paths and caching are checked over HTTP without network access.
 * Payload shapes follow bhashini.gitbook.io/bhashini-apis (pipeline config/compute request + response).
 */
class BhashiniTranslationProviderTest {

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
			record(ex);
			respond(ex, 200, """
					{"languages":[],
					 "pipelineResponseConfig":[{"taskType":"translation","config":[
					   {"serviceId":"ai4bharat/indictrans-v2-all-gpu--t4","modelId":"m1",
					    "language":{"sourceLanguage":"en","targetLanguage":"hi"}}]}],
					 "pipelineInferenceAPIEndPoint":{"callbackUrl":"%s/services/inference/pipeline",
					   "inferenceApiKey":{"name":"Authorization","value":"inference-key-123"},
					   "isMultilingualEnabled":true,"isSyncApi":true}}
					""".formatted(base));
		});
		server.createContext("/services/inference/pipeline", ex -> {
			record(ex);
			if (computeStatus.get() != 200) {
				respond(ex, computeStatus.get(), "{}");
				return;
			}
			respond(ex, 200, """
					{"pipelineResponse":[{"taskType":"translation",
					  "output":[{"source":"Delivery Partner","target":"डिलीवरी पार्टनर"}]}]}
					""");
		});
		server.start();
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void configThenCompute_withTheDocumentedHeadersAndPayloads() {
		BhashiniTranslationProvider provider = new BhashiniTranslationProvider(props());

		assertThat(provider.translate("Delivery Partner", "en", "hi")).isEqualTo("डिलीवरी पार्टनर");

		assertThat(requests).hasSize(2);
		Recorded config = requests.get(0);
		assertThat(config.path()).isEqualTo("/ulca/apis/v0/model/getModelsPipeline");
		assertThat(config.userId()).isEqualTo("user-1");
		assertThat(config.ulcaApiKey()).isEqualTo("ulca-key-1");
		assertThat(config.body()).contains("\"taskType\":\"translation\"", "\"sourceLanguage\":\"en\"",
				"\"targetLanguage\":\"hi\"", "\"pipelineId\":\"pipeline-1\"");

		Recorded compute = requests.get(1);
		assertThat(compute.path()).isEqualTo("/services/inference/pipeline");
		assertThat(compute.authorization()).isEqualTo("inference-key-123");
		assertThat(compute.body()).contains("\"serviceId\":\"ai4bharat/indictrans-v2-all-gpu--t4\"",
				"\"inputData\"", "\"source\":\"Delivery Partner\"");
	}

	@Test
	void pipelineConfig_isCachedPerLanguagePair() {
		BhashiniTranslationProvider provider = new BhashiniTranslationProvider(props());
		provider.translate("Delivery Partner", "en", "hi");
		provider.translate("Delivery Partner", "en", "hi");

		assertThat(requests).extracting(Recorded::path)
				.containsExactly("/ulca/apis/v0/model/getModelsPipeline", "/services/inference/pipeline",
						"/services/inference/pipeline");
	}

	@Test
	void rejectedInferenceKey_dropsTheCachedConfig() {
		BhashiniTranslationProvider provider = new BhashiniTranslationProvider(props());
		provider.translate("Delivery Partner", "en", "hi");
		computeStatus.set(401);
		assertThatThrownBy(() -> provider.translate("Delivery Partner", "en", "hi")).isInstanceOf(RuntimeException.class);
		computeStatus.set(200);

		provider.translate("Delivery Partner", "en", "hi");
		// config, compute, compute(401), then a fresh config before the next compute
		assertThat(requests).extracting(Recorded::path).filteredOn(p -> p.endsWith("getModelsPipeline")).hasSize(2);
	}

	@Test
	void sameLanguage_isANoOp() {
		BhashiniTranslationProvider provider = new BhashiniTranslationProvider(props());
		assertThat(provider.translate("नमस्ते", "hi", "hi")).isEqualTo("नमस्ते");
		assertThat(requests).isEmpty();
	}

	@Test
	void missingCredentials_failFast() {
		assertThatThrownBy(() -> new BhashiniTranslationProvider(new TranslationProperties(
				"bhashini", null, "", "k", "p", null, null, null)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("BHASHINI_USER_ID");
	}

	private TranslationProperties props() {
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		return new TranslationProperties("bhashini", base + "/ulca/apis/v0/model/getModelsPipeline",
				"user-1", "ulca-key-1", "pipeline-1", Duration.ofSeconds(5), Duration.ofHours(1), Duration.ofDays(7));
	}

	private void record(HttpExchange ex) throws IOException {
		requests.add(new Recorded(ex.getRequestURI().getPath(),
				ex.getRequestHeaders().getFirst("userID"),
				ex.getRequestHeaders().getFirst("ulcaApiKey"),
				ex.getRequestHeaders().getFirst("Authorization"),
				new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	private static void respond(HttpExchange ex, int status, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
		ex.sendResponseHeaders(status, bytes.length);
		ex.getResponseBody().write(bytes);
		ex.close();
	}
}
