package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.Application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

	boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

	Optional<Application> findByJobIdAndCandidateId(Long jobId, Long candidateId);

	Page<Application> findByCandidateIdOrderByAppliedAtDesc(Long candidateId, Pageable pageable);

	Page<Application> findByJobIdOrderByAppliedAtDesc(Long jobId, Pageable pageable);
}
