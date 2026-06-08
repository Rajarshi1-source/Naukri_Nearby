package com.naukrinearby.repository;

import java.util.Optional;

import com.naukrinearby.model.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByPhone(String phone);

	boolean existsByPhone(String phone);
}
