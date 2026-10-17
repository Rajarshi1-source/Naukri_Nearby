package com.naukrinearby.repository;

import java.util.List;
import java.util.Optional;

import com.naukrinearby.model.entity.Consent;
import com.naukrinearby.model.enums.ConsentPurpose;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

	/** The current state for one purpose: the newest ledger row. */
	Optional<Consent> findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(Long userId, ConsentPurpose purpose);

	List<Consent> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
}
