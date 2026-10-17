package com.naukrinearby.service.trust;

import java.util.List;

/** Result of scoring a job posting: a 0–100 score plus the signals that produced it. */
public record RiskAssessment(int score, List<RiskSignal> signals) {

	public RiskAssessment {
		signals = List.copyOf(signals);
		score = Math.min(100, Math.max(0, score));
	}

	public String[] reasonCodes() {
		return signals.stream().map(Enum::name).toArray(String[]::new);
	}
}
