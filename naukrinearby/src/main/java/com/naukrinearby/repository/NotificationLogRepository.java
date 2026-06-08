package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.NotificationLog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

	boolean existsByCandidateIdAndJobId(Long candidateId, Long jobId);

	Optional<NotificationLog> findByMessageSid(String messageSid);
}
