package com.naukrinearby.service.trust;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.naukrinearby.config.TrustProperties;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.JobCategory;
import com.naukrinearby.util.ClasspathLines;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Rule-based, explainable fraud scoring for job postings (ADR 0007). Pure CPU work with no network
 * calls, so it runs synchronously on the write path. Phrase lists live in {@code resources/trust/}
 * and are matched as plain substrings; the one regex runs on bounded input and is linear.
 */
@Component
public class JobRiskScorer {

	/** Long free text is truncated before matching so scoring cost stays bounded. */
	static final int MAX_TEXT = 10_000;

	/**
	 * A 10-digit Indian mobile number, optionally prefixed by 91 or 0, with no other digit on either side
	 * (run on whitespace-stripped text, so "+91 98765-43210" is one token).
	 */
	private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(?:91|0)?[6-9]\\d{9}(?!\\d)");

	private static final Map<JobCategory, Integer> MONTHLY_SALARY_CEILING = salaryCeilings();

	private final List<String> paymentPhrases;
	private final List<String> lurePhrases;
	private final TrustProperties props;
	private final Clock clock;

	@Autowired
	public JobRiskScorer(TrustProperties props) {
		this(props, Clock.systemUTC());
	}

	JobRiskScorer(TrustProperties props, Clock clock) {
		this.props = props;
		this.clock = clock;
		this.paymentPhrases = lowerCase(ClasspathLines.read("trust/payment-phrases.txt"));
		this.lurePhrases = lowerCase(ClasspathLines.read("trust/lure-phrases.txt"));
	}

	public RiskAssessment assess(Job job, Employer employer) {
		String title = nz(job.getTitle());
		String text = truncate(title + "\n" + nz(job.getDescription()));
		String lower = text.toLowerCase(Locale.ROOT);

		List<RiskSignal> signals = new ArrayList<>();
		if (containsAny(lower, paymentPhrases)) {
			signals.add(RiskSignal.PAYMENT_REQUEST);
		}
		if (containsAny(lower, lurePhrases)) {
			signals.add(RiskSignal.GET_RICH_QUICK);
		}
		if (salaryLooksUnrealistic(job)) {
			signals.add(RiskSignal.UNREALISTIC_SALARY);
		}
		if (hasOffPlatformContact(lower)) {
			signals.add(RiskSignal.OFF_PLATFORM_CONTACT);
		}
		if (employer == null || !employer.isGstVerified()) {
			signals.add(RiskSignal.UNVERIFIED_BUSINESS);
		}
		if (employer != null && isNew(employer)) {
			signals.add(RiskSignal.NEW_EMPLOYER);
		}
		if (isShouting(title, text)) {
			signals.add(RiskSignal.SHOUTING);
		}
		int score = signals.stream().mapToInt(RiskSignal::weight).sum();
		return new RiskAssessment(score, signals);
	}

	public boolean needsReview(RiskAssessment assessment) {
		return assessment.score() >= props.reviewThreshold();
	}

	private static boolean salaryLooksUnrealistic(Job job) {
		if (job.getSalaryMax() == null || job.getCategory() == null) {
			return false;
		}
		double monthly = toMonthly(job.getSalaryMax(), job.getSalaryType());
		return monthly > MONTHLY_SALARY_CEILING.getOrDefault(job.getCategory(), 150_000);
	}

	static double toMonthly(int amount, String salaryType) {
		String type = salaryType == null ? "MONTHLY" : salaryType.toUpperCase(Locale.ROOT);
		return switch (type) {
			case "HOURLY" -> amount * 208.0;
			case "DAILY" -> amount * 26.0;
			case "WEEKLY" -> amount * 4.33;
			case "YEARLY", "ANNUAL" -> amount / 12.0;
			default -> amount;
		};
	}

	private static boolean hasOffPlatformContact(String lower) {
		if (lower.contains("wa.me/") || lower.contains("chat.whatsapp.com") || lower.contains("t.me/")) {
			return true;
		}
		// Collapse "+91 98765-43210" style formatting before looking for a mobile number.
		StringBuilder compact = new StringBuilder(lower.length());
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			if (c != ' ' && c != '-' && c != '.' && c != '(' && c != ')') {
				compact.append(c);
			}
		}
		return MOBILE.matcher(compact).find();
	}

	private boolean isNew(Employer employer) {
		Instant created = employer.getCreatedAt();
		return created != null
				&& Duration.between(created, clock.instant()).toDays() < props.newEmployerDays();
	}

	private static boolean isShouting(String title, String text) {
		if (text.contains("!!!")) {
			return true;
		}
		int letters = 0;
		int upper = 0;
		for (int i = 0; i < title.length(); i++) {
			char c = title.charAt(i);
			if (c < 128 && Character.isLetter(c)) {
				letters++;
				if (Character.isUpperCase(c)) {
					upper++;
				}
			}
		}
		return letters >= 8 && upper * 10 > letters * 7;
	}

	private static boolean containsAny(String lower, List<String> phrases) {
		for (String p : phrases) {
			if (lower.contains(p)) {
				return true;
			}
		}
		return false;
	}

	private static List<String> lowerCase(List<String> lines) {
		return lines.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList();
	}

	private static String truncate(String s) {
		return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) : s;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	private static Map<JobCategory, Integer> salaryCeilings() {
		Map<JobCategory, Integer> m = new EnumMap<>(JobCategory.class);
		m.put(JobCategory.DELIVERY, 45_000);
		m.put(JobCategory.DRIVER, 50_000);
		m.put(JobCategory.RETAIL, 40_000);
		m.put(JobCategory.ELECTRICAL, 60_000);
		m.put(JobCategory.PLUMBING, 60_000);
		m.put(JobCategory.CONSTRUCTION, 50_000);
		m.put(JobCategory.ACCOUNTING, 80_000);
		m.put(JobCategory.DATA_ENTRY, 40_000);
		m.put(JobCategory.SECURITY, 40_000);
		m.put(JobCategory.HOUSEKEEPING, 35_000);
		m.put(JobCategory.COOKING, 50_000);
		m.put(JobCategory.TAILORING, 40_000);
		m.put(JobCategory.SALES, 70_000);
		return m;
	}
}
