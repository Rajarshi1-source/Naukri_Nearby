package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.Employer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployerRepository extends JpaRepository<Employer, Long> {

	Optional<Employer> findByUserId(Long userId);
}
