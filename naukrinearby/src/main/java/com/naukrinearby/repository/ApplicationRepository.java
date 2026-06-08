package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.Application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

	Optional<Application> findByJobIdAndCandidateId(Long jobId, Long candidateId);

	Page<Application> findByCandidateIdOrderByAppliedAtDesc(Long candidateId, Pageable pageable);

	Page<Application> findByJobIdOrderByAppliedAtDesc(Long jobId, Pageable pageable);

	long countByCandidateId(Long candidateId);

	/** Total applications received across all of an employer's jobs (dashboard stats). */
	@Query("SELECT COUNT(a) FROM Application a, Job j WHERE a.jobId = j.id AND j.employer.id = :employerId")
	long countByEmployerId(@Param("employerId") Long employerId);
}
