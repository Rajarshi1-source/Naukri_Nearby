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

	/**
	 * The prompt template ends with this exact line immediately before the injected resume text.
	 * Heuristics must run on the resume ONLY — the template above this marker lists every canonical
	 * skill and example numbers/emails, which would otherwise leak into every extraction.
	 */
	private static final String RESUME_MARKER = "Output ONLY the JSON object.";

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
		SKILL_MAP.put("pipe fitting", List.of("Pipe Fitting"));
		SKILL_MAP.put("welding", List.of("Welding"));
		SKILL_MAP.put("fitter", List.of("Fitter"));
		SKILL_MAP.put("dukaan", List.of("Retail Sales", "Customer Service"));
		SKILL_MAP.put("kirana", List.of("Retail Sales", "Customer Service"));
	}

	/** Known Indian cities (lowercase -> canonical Title Case) for heuristic city detection. */
	private static final Map<String, String> CITY_MAP = new LinkedHashMap<>();
	static {
		for (String city : List.of(
				"Kanpur", "Pune", "Lucknow", "Jaipur", "Hyderabad", "Indore", "Nagpur", "Bhopal",
				"Mumbai", "Delhi", "Bengaluru", "Bangalore", "Chennai", "Kolkata", "Ahmedabad",
				"Surat", "Patna", "Ranchi", "Bhubaneswar", "Coimbatore", "Kochi", "Visakhapatnam",
				"Vadodara", "Ludhiana", "Agra", "Varanasi", "Nashik", "Rajkot", "Meerut", "Amritsar")) {
			CITY_MAP.put(city.toLowerCase(), city);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T extractStructured(String prompt, Class<T> schema, String promptVersion) {
		if (!schema.equals(ResumeParseResult.class)) {
			throw new UnsupportedOperationException("Stub provider only supports ResumeParseResult");
		}
		String resume = resumeSection(prompt);
		String text = resume.toLowerCase();
		ResumeParseResult r = new ResumeParseResult();

		Matcher phone = PHONE.matcher(resume);
		if (phone.find()) {
			r.setPhone(phone.group(1));
		}
		Matcher email = EMAIL.matcher(resume);
		if (email.find()) {
			r.setEmail(email.group());
		}

		r.setCity(detectCity(text));

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

	/** Returns only the resume portion of a rendered prompt (everything after the final marker line). */
	private static String resumeSection(String prompt) {
		int idx = prompt.lastIndexOf(RESUME_MARKER);
		return idx < 0 ? prompt : prompt.substring(idx + RESUME_MARKER.length());
	}

	/** Returns the canonical name of the first known city mentioned in the resume, or null. */
	private static String detectCity(String lowerText) {
		String best = null;
		int bestIdx = Integer.MAX_VALUE;
		for (var entry : CITY_MAP.entrySet()) {
			Matcher m = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b").matcher(lowerText);
			if (m.find() && m.start() < bestIdx) {
				bestIdx = m.start();
				best = entry.getValue();
			}
		}
		return best;
	}

	@Override
	public String providerId() {
		return "stub";
	}
}
