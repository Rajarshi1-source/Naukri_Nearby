package com.naukrinearby.service.trust;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.naukrinearby.config.TrustProperties;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.JobCategory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JobRiskScorerTest {

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
	private final JobRiskScorer scorer = new JobRiskScorer(new TrustProperties(50, 3, "checksum", 7),
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void ordinaryJob_fromEstablishedVerifiedEmployer_isClean() {
		RiskAssessment r = scorer.assess(job("Delivery Partner", "Deliver parcels in Indore. Bike needed.", 15000),
				employer(true, 90));
		assertThat(r.score()).isZero();
		assertThat(r.signals()).isEmpty();
		assertThat(scorer.needsReview(r)).isFalse();
	}

	@Test
	void ordinaryJob_fromNewUnverifiedEmployer_isFlaggedButApproved() {
		RiskAssessment r = scorer.assess(job("Delivery Partner", "Deliver parcels.", 15000), employer(false, 1));
		assertThat(r.signals()).containsExactly(RiskSignal.UNVERIFIED_BUSINESS, RiskSignal.NEW_EMPLOYER);
		assertThat(r.score()).isEqualTo(20);
		assertThat(scorer.needsReview(r)).isFalse();
	}

	@Test
	void registrationFee_inEnglish_needsReview() {
		RiskAssessment r = scorer.assess(job("Data Entry", "Pay a registration fee of Rs 500 to start", 15000),
				employer(false, 90));
		assertThat(r.signals()).contains(RiskSignal.PAYMENT_REQUEST);
		assertThat(scorer.needsReview(r)).isTrue();
	}

	@Test
	void paymentRequest_inHinglishAndDevanagari_isDetected() {
		assertThat(scorer.assess(job("Helper", "Joining se pehle 300 fees jama karna hoga", 12000), employer(true, 90))
				.signals()).contains(RiskSignal.PAYMENT_REQUEST);
		assertThat(scorer.assess(job("हेल्पर", "काम शुरू करने से पहले पंजीकरण शुल्क देना होगा", 12000), employer(true, 90))
				.signals()).contains(RiskSignal.PAYMENT_REQUEST);
	}

	@Test
	void offPlatformContact_detectsFormattedMobileAndWhatsAppLinks() {
		assertThat(scorer.assess(job("Cook", "Call +91 98765-43210 now", 15000), employer(true, 90)).signals())
				.contains(RiskSignal.OFF_PLATFORM_CONTACT);
		assertThat(scorer.assess(job("Cook", "Message us on wa.me/919876543210", 15000), employer(true, 90)).signals())
				.contains(RiskSignal.OFF_PLATFORM_CONTACT);
		// A pincode or salary figure is not a phone number.
		assertThat(scorer.assess(job("Cook", "Near 226001, salary 15000", 15000), employer(true, 90)).signals())
				.doesNotContain(RiskSignal.OFF_PLATFORM_CONTACT);
	}

	@Test
	void unrealisticSalary_dependsOnCategoryAndPayPeriod() {
		assertThat(scorer.assess(job("Delivery", "Deliver parcels", 200_000), employer(true, 90)).signals())
				.contains(RiskSignal.UNREALISTIC_SALARY);
		Job daily = job("Delivery", "Deliver parcels", 900);
		daily.setSalaryType("DAILY");
		assertThat(scorer.assess(daily, employer(true, 90)).signals()).doesNotContain(RiskSignal.UNREALISTIC_SALARY);
	}

	@Test
	void shoutingTitle_isFlagged() {
		assertThat(scorer.assess(job("URGENT HIRING EARN BIG", "Apply now", 15000), employer(true, 90)).signals())
				.contains(RiskSignal.SHOUTING);
	}

	@Test
	void classicScam_scoresHigh() {
		RiskAssessment r = scorer.assess(job("WORK FROM HOME!!!",
				"Ghar baithe earn daily. Security deposit Rs 999. WhatsApp 9876543210", 15000), employer(false, 0));
		assertThat(r.score()).isGreaterThanOrEqualTo(90);
		assertThat(r.reasonCodes()).contains("PAYMENT_REQUEST", "GET_RICH_QUICK", "OFF_PLATFORM_CONTACT");
	}

	@Test
	void hostileInput_isBoundedAndFast() {
		String huge = "9".repeat(200_000) + "x".repeat(200_000);
		assertThatCode(() -> scorer.assess(job("Helper", huge, 10000), employer(true, 90)))
				.doesNotThrowAnyException();
	}

	private static Job job(String title, String description, int salaryMax) {
		Job j = new Job();
		j.setTitle(title);
		j.setDescription(description);
		j.setCategory(JobCategory.DELIVERY);
		j.setSalaryMax(salaryMax);
		return j;
	}

	private static Employer employer(boolean gstVerified, int ageDays) {
		Employer e = new Employer();
		e.setGstVerified(gstVerified);
		e.setCreatedAt(NOW.minus(Duration.ofDays(ageDays)));
		return e;
	}
}
