package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.NotificationPreference;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

	Optional<NotificationPreference> findByUserId(Long userId);
}
