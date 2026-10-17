package com.naukrinearby.repository;

import java.time.Instant;

import com.naukrinearby.model.entity.JobReport;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobReportRepository extends JpaRepository<JobReport, Long> {

	boolean existsByJobIdAndReporterId(Long jobId, Long reporterId);

	long countByJobId(Long jobId);

	long countByJobIdAndCreatedAtAfter(Long jobId, Instant after);
}
