package com.naukrinearby.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukrinearby.exception.DuplicateApplicationException;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.util.PhoneNumberValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * WhatsApp-first apply (master plan G1, the headline differentiator): a candidate with no app and no
 * resume applies entirely over WhatsApp. State for the multi-turn chat lives in Redis keyed by phone;
 * the collected answers are run through the same resume-extraction pipeline used for uploaded resumes,
 * the profile is upserted, and the application is created idempotently. Prompts are bilingual
 * (English + हिंदी) so the flow works for low-literacy and regional users.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppConversationService {

	private static final Duration SESSION_TTL = Duration.ofMinutes(30);
	private static final String SESSION = "wa:session:";
	private static final Pattern APPLY_JOB = Pattern.compile("(?:apply|aavedan)\\D*(\\d+)",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern DEVANAGARI = Pattern.compile("[\\u0900-\\u097F]");

	private final StringRedisTemplate redis;
	private final AuthService authService;
	private final ResumeExtractionService extractionService;
	private final CandidateProfileService profileService;
	private final ApplicationService applicationService;

	private enum State {
		ASK_NAME, ASK_SKILLS, ASK_EXPERIENCE
	}

	/** Handles one inbound WhatsApp message and returns the reply text to send back. */
	public String handleInbound(String fromPhone, String body) {
		String phone;
		try {
			phone = PhoneNumberValidator.normalize(stripWhatsApp(fromPhone));
		}
		catch (RuntimeException ex) {
			return "Sorry, we couldn't read your number. / माफ़ कीजिए, आपका नंबर नहीं पढ़ पाए।";
		}
		String text = body == null ? "" : body.trim();
		String key = SESSION + phone;
		Map<Object, Object> session = redis.opsForHash().entries(key);

		if (session.isEmpty()) {
			return start(key, text);
		}
		String state = (String) session.get("state");
		if (State.ASK_NAME.name().equals(state)) {
			put(key, "name", text);
			setState(key, State.ASK_SKILLS);
			return "Thanks " + text + "! What work/skills do you have? "
					+ "(e.g. Plumbing, Tally)\nआपके पास कौन से काम/स्किल हैं?";
		}
		if (State.ASK_SKILLS.name().equals(state)) {
			put(key, "skills", text);
			setState(key, State.ASK_EXPERIENCE);
			return "How many years of experience do you have?\nआपके पास कितने साल का अनुभव है?";
		}
		if (State.ASK_EXPERIENCE.name().equals(state)) {
			put(key, "experience", text);
			return finish(key, phone);
		}
		// Unknown state — reset.
		redis.delete(key);
		return start(key, text);
	}

	private String start(String key, String text) {
		Matcher m = APPLY_JOB.matcher(text);
		if (m.find()) {
			put(key, "jobId", m.group(1));
		}
		put(key, "lang", DEVANAGARI.matcher(text).find() ? "hi" : "en");
		setState(key, State.ASK_NAME);
		redis.expire(key, SESSION_TTL);
		return "Welcome to NaukriNearby! What is your name?\n"
				+ "नौकरी नियरबाय में आपका स्वागत है! आपका नाम क्या है?";
	}

	private String finish(String key, String phone) {
		Map<Object, Object> session = redis.opsForHash().entries(key);
		String name = (String) session.get("name");
		String skills = (String) session.get("skills");
		String experience = (String) session.get("experience");
		String lang = (String) session.getOrDefault("lang", "en");
		String jobIdRaw = (String) session.get("jobId");

		String transcript = "Name: " + nz(name) + "\n"
				+ "Phone: " + phone + "\n"
				+ "Skills: " + nz(skills) + "\n"
				+ "Experience: " + nz(experience);

		User user;
		try {
			user = authService.getOrCreateCandidate(phone);
			ResumeParseResult result = extractionService.extract(transcript);
			if (name != null && !name.isBlank()) {
				result.setName(name.trim());
			}
			result.setPhone(phone);
			if (result.getLanguagesSpoken() == null || result.getLanguagesSpoken().isEmpty()) {
				result.setLanguagesSpoken(List.of(lang));
			}
			profileService.applyParsedResume(user.getId(), result, null);
		}
		catch (RuntimeException ex) {
			log.warn("WhatsApp profile build failed for user: {}", ex.getMessage());
			redis.delete(key);
			return "Sorry, something went wrong. Please try again later.\n"
					+ "माफ़ कीजिए, कुछ गड़बड़ हुई। बाद में फिर कोशिश करें।";
		}

		String reply = applyIfRequested(jobIdRaw, user.getId());
		redis.delete(key);
		return reply;
	}

	private String applyIfRequested(String jobIdRaw, Long candidateId) {
		if (jobIdRaw == null || jobIdRaw.isBlank()) {
			return "Your profile is ready! Reply 'apply <job number>' to apply for a job.\n"
					+ "आपकी प्रोफ़ाइल तैयार है! नौकरी के लिए 'apply <नंबर>' भेजें।";
		}
		Long jobId = Long.valueOf(jobIdRaw);
		try {
			applicationService.apply(jobId, candidateId, "Applied via WhatsApp");
			return "Done! You have applied for job #" + jobId + ".\n"
					+ "हो गया! आपने नौकरी #" + jobId + " के लिए आवेदन कर दिया है।";
		}
		catch (DuplicateApplicationException dup) {
			return "You have already applied for job #" + jobId + ".\n"
					+ "आप पहले ही नौकरी #" + jobId + " के लिए आवेदन कर चुके हैं।";
		}
		catch (RuntimeException ex) {
			log.warn("WhatsApp apply failed job={} : {}", jobId, ex.getMessage());
			return "Sorry, that job is not available.\nमाफ़ कीजिए, वह नौकरी उपलब्ध नहीं है।";
		}
	}

	private void put(String key, String field, String value) {
		redis.opsForHash().put(key, field, value == null ? "" : value);
		redis.expire(key, SESSION_TTL);
	}

	private void setState(String key, State state) {
		redis.opsForHash().put(key, "state", state.name());
		redis.expire(key, SESSION_TTL);
	}

	private static String stripWhatsApp(String from) {
		if (from == null) {
			return null;
		}
		return from.startsWith("whatsapp:") ? from.substring("whatsapp:".length()) : from;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
