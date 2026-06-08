package com.naukrinearby.repository;

import java.util.List;

import com.naukrinearby.model.entity.SavedJob;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {

	List<SavedJob> findByUserIdOrderByCreatedAtDesc(Long userId);

	boolean existsByUserIdAndJobId(Long userId, Long jobId);

	long deleteByUserIdAndJobId(Long userId, Long jobId);
}
