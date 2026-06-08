package com.naukrinearby.util;

import java.util.Locale;
import java.util.UUID;

public final class SlugGenerator {

	private SlugGenerator() {
	}

	/** Title -> URL slug with a short random suffix to guarantee uniqueness. */
	public static String slugify(String title) {
		String base = title == null ? "job" : title.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9\\s-]", "")
				.replaceAll("\\s+", "-")
				.replaceAll("-{2,}", "-")
				.replaceAll("^-|-$", "");
		if (base.isBlank()) {
			base = "job";
		}
		if (base.length() > 60) {
			base = base.substring(0, 60).replaceAll("-$", "");
		}
		return base + "-" + UUID.randomUUID().toString().substring(0, 8);
	}
}
