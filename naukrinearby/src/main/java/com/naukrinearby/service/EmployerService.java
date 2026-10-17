package com.naukrinearby.service;

import java.time.Instant;
import java.util.Locale;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.dto.EmployerProfileDto;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.service.trust.GstVerifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employer profile and business verification (ADR 0007). A GSTIN that passes {@link GstVerifier}
 * marks the employer {@code BUSINESS_VERIFIED}. Company name and trust level are denormalized into
 * every job's search document, so a profile change re-emits outbox events for the employer's jobs —
 * the index is never written directly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployerService {

	private final EmployerRepository employerRepo;
	private final JobRepository jobRepo;
	private final JobOutboxWriter outboxWriter;
	private final GstVerifier gstVerifier;

	@Transactional(readOnly = true)
	public Employer get(Long userId) {
		return employerRepo.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("Employer profile not found"));
	}

	@Transactional
	public Employer update(Long userId, EmployerProfileDto.Request req) {
		Employer e = get(userId);
		String gstin = req.gstNumber() == null || req.gstNumber().isBlank()
				? null : req.gstNumber().strip().toUpperCase(Locale.ROOT);
		if (gstin != null && !gstVerifier.isValid(gstin) && "checksum".equals(gstVerifier.verifierId())) {
			throw new ValidationException("GSTIN is not valid. Check the 15-character number on your GST certificate.");
		}
		boolean companyChanged = !req.companyName().strip().equals(e.getCompanyName());
		boolean wasVerified = e.isGstVerified();

		e.setCompanyName(req.companyName().strip());
		e.setCompanyType(blankToNull(req.companyType()));
		e.setAddress(blankToNull(req.address()));
		e.setCity(blankToNull(req.city()));
		e.setState(blankToNull(req.state()));
		e.setGstNumber(gstin);
		boolean verified = gstin != null && gstVerifier.isValid(gstin);
		e.setGstVerified(verified);
		e.setVerifiedAt(verified ? (wasVerified && e.getVerifiedAt() != null ? e.getVerifiedAt() : Instant.now()) : null);
		e = employerRepo.save(e);

		if (companyChanged || wasVerified != verified) {
			reindexJobs(e);
		}
		return e;
	}

	private void reindexJobs(Employer employer) {
		int count = 0;
		for (Job job : jobRepo.findByEmployer_Id(employer.getId())) {
			job.setEmployer(employer);
			job.setVersion(job.getVersion() + 1);
			jobRepo.save(job);
			outboxWriter.write(job, "JOB_UPDATED");
			count++;
		}
		log.info("Employer {} profile changed; re-emitted {} job events", employer.getId(), count);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
