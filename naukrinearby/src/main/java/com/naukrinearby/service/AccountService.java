package com.naukrinearby.service;

import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle, including DPDP right-to-erasure: deleting a user cascades all owned rows
 * (FKs are {@code ON DELETE CASCADE}) and purges the resume object from MinIO so no PII is left behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final UserRepository userRepo;
	private final CandidateProfileRepository profileRepo;
	private final FileStorageService fileStorage;

	@Transactional
	public void deleteAccount(Long userId) {
		String resumeKey = profileRepo.findByUserId(userId)
				.map(CandidateProfile::getResumeFileKey)
				.orElse(null);
		userRepo.deleteById(userId);
		if (resumeKey != null) {
			fileStorage.delete(resumeKey);
		}
		log.info("Erased account and resume object for user {}", userId);
	}
}
