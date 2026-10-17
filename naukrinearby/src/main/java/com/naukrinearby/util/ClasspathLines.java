package com.naukrinearby.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/** Reads small UTF-8 data files from the classpath: one entry per line, blank lines and {@code #} comments skipped. */
public final class ClasspathLines {

	private ClasspathLines() {
	}

	public static List<String> read(String path) {
		List<String> out = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(
				new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				String t = line.strip();
				if (!t.isEmpty() && !t.startsWith("#")) {
					out.add(t);
				}
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot load classpath resource " + path, ex);
		}
		return List.copyOf(out);
	}
}
