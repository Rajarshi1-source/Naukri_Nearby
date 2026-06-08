package com.naukrinearby.model.dto;

/**
 * Dashboard counters (master plan §7.3). Candidate view fills {@code totalApplications} +
 * {@code profileCompleteness}; employer view fills the job/application totals. Unused fields are null.
 */
public record DashboardStatsDTO(
		Long totalJobs,
		Long activeJobs,
		Long totalApplications,
		Integer profileCompleteness) {

	public static DashboardStatsDTO forCandidate(long totalApplications, int profileCompleteness) {
		return new DashboardStatsDTO(null, null, totalApplications, profileCompleteness);
	}

	public static DashboardStatsDTO forEmployer(long totalJobs, long activeJobs, long totalApplications) {
		return new DashboardStatsDTO(totalJobs, activeJobs, totalApplications, null);
	}
}
