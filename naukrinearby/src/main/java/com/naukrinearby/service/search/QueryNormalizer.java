package com.naukrinearby.service.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.naukrinearby.util.ClasspathLines;

import org.springframework.stereotype.Component;

/**
 * Maps Hinglish (Latin-script Hindi) and Devanagari job words to the English terms used in job
 * titles and skills, so "bijli mistri ka kaam chahiye" or "बिजली मिस्त्री" finds electrician jobs
 * (plan D9). Filler words ("naukri", "chahiye", "काम") are dropped. Unknown words pass through, so
 * the Elasticsearch Hindi analyzer still sees them.
 *
 * <p>The lexicon is {@code resources/lexicon/hinglish.tsv}: {@code variant<TAB>canonical}, phrases of
 * up to {@value #MAX_PHRASE_WORDS} words, canonical {@code -} for fillers. Matching is a greedy,
 * longest-phrase-first scan over whitespace tokens — linear in the query length, no regex.
 */
@Component
public class QueryNormalizer {

	static final int MAX_PHRASE_WORDS = 3;
	static final int MAX_QUERY_CHARS = 200;
	private static final String FILLER = "-";

	private final Map<String, String> lexicon;

	public QueryNormalizer() {
		Map<String, String> map = new HashMap<>();
		for (String line : ClasspathLines.read("lexicon/hinglish.tsv")) {
			String[] parts = line.split("\t");
			if (parts.length == 2) {
				map.put(normalizeSpaces(parts[0].toLowerCase(Locale.ROOT)), parts[1].strip().toLowerCase(Locale.ROOT));
			}
		}
		this.lexicon = Map.copyOf(map);
	}

	/**
	 * Returns the search text to send to the index: canonical terms for known words plus any unknown
	 * words, de-duplicated in order. Returns the trimmed input if everything was filler, and null for
	 * a blank query.
	 */
	public String normalize(String query) {
		if (query == null || query.isBlank()) {
			return null;
		}
		String q = query.strip();
		if (q.length() > MAX_QUERY_CHARS) {
			q = q.substring(0, MAX_QUERY_CHARS);
		}
		Set<String> terms = new LinkedHashSet<>(translate(q));
		return terms.isEmpty() ? q : String.join(" ", terms);
	}

	/** Canonical form of a single skill or job word, used by the skill-gap matcher. */
	public String canonicalSkill(String skill) {
		if (skill == null) {
			return "";
		}
		String key = normalizeSpaces(skill.toLowerCase(Locale.ROOT));
		String canonical = lexicon.get(key);
		return canonical == null || FILLER.equals(canonical) ? key : canonical;
	}

	private List<String> translate(String q) {
		String[] tokens = normalizeSpaces(q.toLowerCase(Locale.ROOT)).split(" ");
		List<String> out = new ArrayList<>();
		int i = 0;
		while (i < tokens.length) {
			int matched = 0;
			String canonical = null;
			for (int n = Math.min(MAX_PHRASE_WORDS, tokens.length - i); n >= 1; n--) {
				String phrase = String.join(" ", java.util.Arrays.copyOfRange(tokens, i, i + n));
				String hit = lexicon.get(stripPunctuation(phrase));
				if (hit != null) {
					matched = n;
					canonical = hit;
					break;
				}
			}
			if (matched == 0) {
				String word = stripPunctuation(tokens[i]);
				if (!word.isEmpty()) {
					out.add(word);
				}
				i++;
			}
			else {
				if (!FILLER.equals(canonical)) {
					out.add(canonical);
				}
				i += matched;
			}
		}
		return out;
	}

	private static String normalizeSpaces(String s) {
		StringBuilder b = new StringBuilder(s.length());
		boolean space = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isWhitespace(c)) {
				space = b.length() > 0;
			}
			else {
				if (space) {
					b.append(' ');
					space = false;
				}
				b.append(c);
			}
		}
		return b.toString();
	}

	private static String stripPunctuation(String s) {
		int start = 0;
		int end = s.length();
		while (start < end && isPunct(s.charAt(start))) {
			start++;
		}
		while (end > start && isPunct(s.charAt(end - 1))) {
			end--;
		}
		return s.substring(start, end);
	}

	private static boolean isPunct(char c) {
		return c == ',' || c == '.' || c == '?' || c == '!' || c == ';' || c == ':' || c == '।' || c == '"' || c == '\'';
	}
}
