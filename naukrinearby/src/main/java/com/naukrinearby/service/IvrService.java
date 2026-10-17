package com.naukrinearby.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.naukrinearby.exception.DuplicateApplicationException;
import com.naukrinearby.generation.WhatsAppSender;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.model.enums.ConsentPurpose;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.repository.UserRepository;
import com.naukrinearby.util.GeoUtils;
import com.naukrinearby.util.PhoneNumberValidator;
import com.naukrinearby.util.PiiRedactor;
import com.naukrinearby.util.TwiML;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Voice-first apply over a phone call (ADR 0008). Twilio posts each step to a webhook and plays back
 * the TwiML returned here. Call state lives in Redis keyed by {@code CallSid}, so every request is
 * otherwise stateless. Prompts are Hindi first, then English; callers answer with keypad digits or by
 * speaking. Applying reuses {@link ApplicationService#apply}, which is idempotent.
 *
 * <p>Menu: 1 = apply to a job by its number, 2 = hear jobs near my saved location, 9 = repeat.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IvrService {

	public static final String BASE = "/api/webhooks/twilio/voice";
	static final Duration CALL_TTL = Duration.ofMinutes(15);
	static final String KEY = "ivr:call:";
	static final int MAX_JOBS_READ = 3;
	private static final int MAX_DIGITS = 12;

	private final StringRedisTemplate redis;
	private final AuthService authService;
	private final ApplicationService applicationService;
	private final JobService jobService;
	private final UserRepository userRepo;
	private final CandidateProfileRepository profileRepo;
	private final JobRepository jobRepo;
	private final WhatsAppSender whatsAppSender;
	private final ConsentService consentService;

	enum Choice {
		APPLY, NEARBY, REPEAT, YES, NO, UNKNOWN
	}

	/** Incoming call: greet and play the main menu. */
	public String start(String callSid, String from) {
		String phone = normalize(from);
		if (callSid == null || phone == null) {
			return TwiML.response()
					.say("माफ़ कीजिए, हम आपका नंबर नहीं पहचान पाए।", "hi-IN")
					.say("Sorry, we could not read your number.", "en-IN")
					.hangup().build();
		}
		redis.opsForHash().put(KEY + callSid, "phone", phone);
		redis.expire(KEY + callSid, CALL_TTL);
		return menu(TwiML.response()
				.say("नौकरी नियरबाय में आपका स्वागत है।", "hi-IN")
				.say("Welcome to Naukri Nearby.", "en-IN")).build();
	}

	/** Main-menu answer. */
	public String onMenu(String callSid, String digits, String speech) {
		String phone = phoneFor(callSid);
		if (phone == null) {
			return restart();
		}
		return switch (choice(digits, speech)) {
			case APPLY -> askJobNumber(TwiML.response()).build();
			case NEARBY -> nearbyJobs(phone);
			case REPEAT -> menu(TwiML.response()).build();
			default -> menu(TwiML.response()
					.say("माफ़ कीजिए, समझ नहीं आया।", "hi-IN")
					.say("Sorry, I did not understand.", "en-IN")).build();
		};
	}

	/** The caller entered or spoke a job number: read the job back and ask for confirmation. */
	public String onJobNumber(String callSid, String digits, String speech) {
		if (phoneFor(callSid) == null) {
			return restart();
		}
		String number = extractDigits(digits != null && !digits.isBlank() ? digits : speech);
		Optional<Job> job = number.isEmpty() ? Optional.empty() : findPublicJob(Long.valueOf(number));
		if (job.isEmpty()) {
			return askJobNumber(TwiML.response()
					.say("यह नौकरी नंबर नहीं मिला।", "hi-IN")
					.say("We could not find that job number.", "en-IN")).build();
		}
		Job j = job.get();
		redis.opsForHash().put(KEY + callSid, "jobId", String.valueOf(j.getId()));
		redis.expire(KEY + callSid, CALL_TTL);
		String summary = describe(j);
		return TwiML.response()
				.gather(BASE + "/gather/confirm", 1, null, "hi-IN", "haan, nahin, yes, no, ek, do", t -> t
						.say("नौकरी: " + summary + "। आवेदन करने के लिए 1 दबाएं, रद्द करने के लिए 2।", "hi-IN")
						.say("Job: " + summary + ". Press 1 to apply, or 2 to cancel.", "en-IN"))
				.redirect(BASE + "/gather/confirm")
				.build();
	}

	/** Confirmation: apply (idempotent) and send a WhatsApp receipt. */
	public String onConfirm(String callSid, String digits, String speech) {
		String phone = phoneFor(callSid);
		Object jobIdRaw = redis.opsForHash().get(KEY + callSid, "jobId");
		if (phone == null || jobIdRaw == null) {
			return restart();
		}
		Choice c = choice(digits, speech);
		if (c != Choice.YES && c != Choice.APPLY) {
			redis.opsForHash().delete(KEY + callSid, "jobId");
			return menu(TwiML.response()
					.say("ठीक है, आवेदन रद्द किया।", "hi-IN")
					.say("Okay, cancelled.", "en-IN")).build();
		}
		Long jobId = Long.valueOf(jobIdRaw.toString());
		User user = authService.getOrCreateCandidate(phone);
		try {
			applicationService.apply(jobId, user.getId(), "Applied by phone call (IVR)");
		}
		catch (DuplicateApplicationException dup) {
			return goodbye(TwiML.response()
					.say("आप इस नौकरी के लिए पहले ही आवेदन कर चुके हैं।", "hi-IN")
					.say("You have already applied for this job.", "en-IN"));
		}
		catch (RuntimeException ex) {
			log.warn("IVR apply failed for job {}: {}", jobId, ex.getMessage());
			return goodbye(TwiML.response()
					.say("माफ़ कीजिए, यह नौकरी अभी उपलब्ध नहीं है।", "hi-IN")
					.say("Sorry, this job is not available right now.", "en-IN"));
		}
		sendReceipt(user, phone, jobId);
		log.info("IVR apply: job {} by {}", jobId, PiiRedactor.maskPhone(phone));
		return goodbye(TwiML.response()
				.say("हो गया! आपका आवेदन भेज दिया गया है। नियोक्ता आपसे संपर्क करेगा।", "hi-IN")
				.say("Done! Your application has been sent. The employer will contact you.", "en-IN"));
	}

	private String nearbyJobs(String phone) {
		Optional<CandidateProfile> profile = userRepo.findByPhone(phone)
				.flatMap(u -> profileRepo.findByUserId(u.getId()))
				.filter(p -> p.getLocation() != null);
		if (profile.isEmpty()) {
			return goodbye(TwiML.response()
					.say("आपकी प्रोफ़ाइल में जगह नहीं है। व्हाट्सऐप पर hi भेजकर प्रोफ़ाइल बनाएं।", "hi-IN")
					.say("We don't have your location yet. Send hi on WhatsApp to create your profile.", "en-IN"));
		}
		CandidateProfile p = profile.get();
		int radiusKm = p.getPreferredRadiusKm() == null ? 10 : p.getPreferredRadiusKm();
		List<Job> jobs = jobRepo.findWithinRadius(GeoUtils.latOf(p.getLocation()), GeoUtils.lngOf(p.getLocation()),
				radiusKm * 1000.0, MAX_JOBS_READ);
		TwiML t = TwiML.response();
		if (jobs.isEmpty()) {
			t.say("अभी आपके पास कोई नौकरी नहीं है।", "hi-IN").say("There are no jobs near you right now.", "en-IN");
		}
		else {
			t.say("आपके पास की नौकरियां।", "hi-IN").say("Jobs near you.", "en-IN");
			for (Job j : jobs) {
				t.say("नौकरी नंबर " + j.getId() + "। " + describe(j), "hi-IN");
			}
		}
		return menu(t).build();
	}

	private TwiML menu(TwiML t) {
		return t.gather(BASE + "/gather/menu", 1, null, "hi-IN", "ek, do, nau, one, two, nine, apply, aavedan", g -> g
				.say("नौकरी नंबर से आवेदन करने के लिए 1 दबाएं। अपने पास की नौकरियां सुनने के लिए 2 दबाएं। दोबारा सुनने के लिए 9 दबाएं।", "hi-IN")
				.say("To apply with a job number, press 1. To hear jobs near you, press 2. To repeat, press 9.", "en-IN"))
				.redirect(BASE + "/gather/menu");
	}

	private static TwiML askJobNumber(TwiML t) {
		return t.gather(BASE + "/gather/job", 0, "#", "hi-IN", null, g -> g
				.say("नौकरी नंबर डालें और फिर हैश दबाएं।", "hi-IN")
				.say("Enter the job number, then press hash.", "en-IN"))
				.redirect(BASE + "/gather/menu");
	}

	private static String goodbye(TwiML t) {
		return t.say("धन्यवाद।", "hi-IN").say("Thank you.", "en-IN").hangup().build();
	}

	private static String restart() {
		return TwiML.response().redirect(BASE).build();
	}

	private Optional<Job> findPublicJob(Long id) {
		try {
			return Optional.of(jobService.getPublicJob(id));
		}
		catch (RuntimeException ex) {
			return Optional.empty();
		}
	}

	private void sendReceipt(User user, String phone, Long jobId) {
		if (consentService.isWithdrawn(user.getId(), ConsentPurpose.WHATSAPP_ALERTS)) {
			return;
		}
		try {
			whatsAppSender.send(phone, "NaukriNearby: you applied for job #" + jobId + " by phone call.\n"
					+ "आपने फ़ोन कॉल से नौकरी #" + jobId + " के लिए आवेदन किया।");
		}
		catch (RuntimeException ex) {
			log.warn("IVR WhatsApp receipt failed: {}", ex.getMessage());
		}
	}

	private static String describe(Job j) {
		StringBuilder b = new StringBuilder(j.getTitle());
		if (j.getCity() != null) {
			b.append(", ").append(j.getCity());
		}
		if (j.getSalaryMin() != null && j.getSalaryMax() != null) {
			b.append(", ₹").append(j.getSalaryMin()).append(" – ₹").append(j.getSalaryMax());
		}
		else if (j.getSalaryMax() != null) {
			b.append(", ₹").append(j.getSalaryMax());
		}
		return b.toString();
	}

	private String phoneFor(String callSid) {
		if (callSid == null) {
			return null;
		}
		Map<Object, Object> session = redis.opsForHash().entries(KEY + callSid);
		Object phone = session.get("phone");
		return phone == null ? null : phone.toString();
	}

	static Choice choice(String digits, String speech) {
		String d = digits == null ? "" : digits.strip();
		if (!d.isEmpty()) {
			return switch (d.charAt(0)) {
				case '1' -> Choice.APPLY;
				case '2' -> Choice.NEARBY;
				case '9' -> Choice.REPEAT;
				default -> Choice.UNKNOWN;
			};
		}
		String s = speech == null ? "" : speech.toLowerCase(Locale.ROOT);
		if (s.length() > 100) {
			s = s.substring(0, 100);
		}
		if (containsWord(s, "haan", "han", "yes", "हाँ", "हां", "ji")) {
			return Choice.YES;
		}
		if (containsWord(s, "nahin", "nahi", "no", "नहीं", "cancel")) {
			return Choice.NO;
		}
		if (containsWord(s, "1", "one", "ek", "एक", "१", "apply", "aavedan", "आवेदन")) {
			return Choice.APPLY;
		}
		if (containsWord(s, "2", "two", "do", "दो", "२", "suno", "sunna", "nearby", "paas")) {
			return Choice.NEARBY;
		}
		if (containsWord(s, "9", "nine", "nau", "नौ", "९", "repeat", "dobara")) {
			return Choice.REPEAT;
		}
		return Choice.UNKNOWN;
	}

	private static boolean containsWord(String text, String... words) {
		for (String token : text.split("[\\s,.!?।]+")) {
			for (String w : words) {
				if (token.equals(w)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Keypad or spoken digits (ASCII or Devanagari) → an ASCII digit string, at most 12 long. */
	static String extractDigits(String input) {
		if (input == null) {
			return "";
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < input.length() && b.length() < MAX_DIGITS; i++) {
			char c = input.charAt(i);
			if (Character.isDigit(c)) {
				b.append(Character.digit(c, 10));
			}
		}
		String out = b.toString().replaceFirst("^0+(?=\\d)", "");
		return out;
	}

	private static String normalize(String from) {
		if (from == null) {
			return null;
		}
		try {
			return PhoneNumberValidator.normalize(from.startsWith("whatsapp:") ? from.substring(9) : from);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}
}
