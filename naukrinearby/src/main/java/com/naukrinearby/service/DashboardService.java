package com.naukrinearby.service;

import com.naukrinearby.exception.ForbiddenException;
import com.naukrinearby.model.dto.DashboardStatsDTO;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.model.enums.JobStatus;
import com.naukrinearby.repository.ApplicationRepository;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.JobRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only dashboard counters for candidate and employer home screens (master plan §7.3). */
@Service
@RequiredArgsConstructor
public class DashboardService {

	private final ApplicationRepository applicationRepo;
	private final JobRepository jobRepo;
	private final EmployerRepository employerRepo;
	private final CandidateProfileRepository profileRepo;

	@Transactional(readOnly = true)
	public DashboardStatsDTO candidateStats(Long userId) {
		long applications = applicationRepo.countByCandidateId(userId);
		int completeness = profileRepo.findByUserId(userId)
				.map(CandidateProfile::getProfileCompleteness)
				.orElse(0);
		return DashboardStatsDTO.forCandidate(applications, completeness);
	}

	@Transactional(readOnly = true)
	public DashboardStatsDTO employerStats(Long employerUserId) {
		var employer = employerRepo.findByUserId(employerUserId)
				.orElseThrow(() -> new ForbiddenException("Employer profile not found"));
		long totalJobs = jobRepo.countByEmployer_Id(employer.getId());
		long activeJobs = jobRepo.countByEmployer_IdAndStatus(employer.getId(), JobStatus.ACTIVE);
		long applications = applicationRepo.countByEmployerId(employer.getId());
		return DashboardStatsDTO.forEmployer(totalJobs, activeJobs, applications);
	}
}
