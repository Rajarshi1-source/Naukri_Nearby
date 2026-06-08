package com.naukrinearby.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukrinearby.model.dto.ResumeParseResult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Heuristic, offline extraction provider used when {@code naukri.llm.extraction-provider=stub}.
 * Not as accurate as the real model — it exists so the app and the eval harness run with no API key.
 * Only supports {@link ResumeParseResult}.
 */
@Component
@ConditionalOnProperty(name = "naukri.llm.extraction-provider", havingValue = "stub")
public class StubLlmExtractionProvider implements LlmExtractionProvider {

	private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?91[-\\s]?|0)?([6-9]\\d{9})(?!\\d)");
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
	private static final Pattern YEARS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(saal|years?|yr)");
	private static final Pattern MONTHS = Pattern.compile("(\\d+)\\s*(mahine|months?)");

	private static final Map<String, List<String>> SKILL_MAP = new LinkedHashMap<>();
	static {
		SKILL_MAP.put("tally", List.of("Tally"));
		SKILL_MAP.put("excel", List.of("MS Excel"));
		SKILL_MAP.put("accounting", List.of("Accounting"));
		SKILL_MAP.put("gst", List.of("GST Filing"));
		SKILL_MAP.put("data entry", List.of("Data Entry"));
		SKILL_MAP.put("typing", List.of("Typing"));
		SKILL_MAP.put("delivery", List.of("Delivery", "Two-Wheeler Driving", "Logistics"));
		SKILL_MAP.put("electrician", List.of("Electrical Wiring"));
		SKILL_MAP.put("wiring", List.of("Electrical Wiring"));
		SKILL_MAP.put("motor", List.of("Motor Repair"));
		SKILL_MAP.put("inverter", List.of("Inverter Repair"));
		SKILL_MAP.put("plumber", List.of("Plumbing"));
		SKILL_MAP.put("plumbing", List.of("Plumbing"));
		SKILL_MAP.put("welding", List.of("Welding"));
		SKILL_MAP.put("fitter", List.of("Fitter"));
		SKILL_MAP.put("dukaan", List.of("Retail Sales", "Customer Service"));
		SKILL_MAP.put("kirana", List.of("Retail Sales", "Customer Service"));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T extractStructured(String prompt, Class<T> schema, String promptVersion) {
		if (!schema.equals(ResumeParseResult.class)) {
			throw new UnsupportedOperationException("Stub provider only supports ResumeParseResult");
		}
		String text = prompt.toLowerCase();
		ResumeParseResult r = new ResumeParseResult();

		Matcher phone = PHONE.matcher(prompt);
		if (phone.find()) {
			r.setPhone(phone.group(1));
		}
		Matcher email = EMAIL.matcher(prompt);
		if (email.find()) {
			r.setEmail(email.group());
		}

		List<String> skills = new ArrayList<>();
		for (var entry : SKILL_MAP.entrySet()) {
			if (text.contains(entry.getKey())) {
				for (String s : entry.getValue()) {
					if (!skills.contains(s)) {
						skills.add(s);
					}
				}
			}
		}
		r.setSkills(skills);

		int months = 0;
		Matcher y = YEARS.matcher(text);
		if (y.find()) {
			months += Math.round(Float.parseFloat(y.group(1)) * 12);
		}
		Matcher m = MONTHS.matcher(text);
		if (m.find()) {
			months += Integer.parseInt(m.group(1));
		}
		r.setTotalExperienceMonths(months);
		r.setPromptVersion(promptVersion);
		return (T) r;
	}

	@Override
	public String providerId() {
		return "stub";
	}
}
