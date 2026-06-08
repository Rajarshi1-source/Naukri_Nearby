package com.naukrinearby.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads and caches versioned prompts from {@code resources/prompts/{version}.txt} (eval connectors §3). */
@Slf4j
@Component
public class PromptLoader {

	private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

	public String load(String version) {
		return cache.computeIfAbsent(version, this::readFromClasspath);
	}

	private String readFromClasspath(String version) {
		String path = "prompts/" + version + ".txt";
		try {
			var resource = new ClassPathResource(path);
			String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			log.info("Loaded prompt '{}' ({} chars)", version, content.length());
			return content;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Prompt not found on classpath: " + path, ex);
		}
	}
}
