package com.naukrinearby.service;

import java.util.Arrays;
import java.util.List;

import com.naukrinearby.model.dto.ConsentDto;
import com.naukrinearby.model.entity.Consent;
import com.naukrinearby.model.enums.ConsentPurpose;
import com.naukrinearby.repository.ConsentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DPDP notice-and-consent ledger (plan D11). Every change appends a row; the newest row per purpose
 * is the current state. Withdrawing {@code WHATSAPP_ALERTS} stops alerts at the notification worker.
 */
@Service
@RequiredArgsConstructor
public class ConsentService {

	/** Version of the privacy notice shown in the web app when a user records a choice. */
	public static final String WEB_NOTICE_VERSION = "web-2026-10";
	/** Recorded when a user starts the WhatsApp apply chat (they send us their details to build a profile). */
	public static final String WHATSAPP_NOTICE_VERSION = "whatsapp-chat-2026-10";
	/** Recorded when a user uploads a resume after seeing the upload notice. */
	public static final String UPLOAD_NOTICE_VERSION = "resume-upload-2026-10";

	private final ConsentRepository repo;

	@Transactional
	public ConsentDto.Response record(Long userId, ConsentPurpose purpose, boolean granted, String noticeVersion) {
		Consent saved = repo.save(new Consent(userId, purpose, granted, noticeVersion));
		return toResponse(purpose, saved);
	}

	/** Records a grant only if the current state is not already granted (keeps the ledger short). */
	@Transactional
	public void grantIfAbsent(Long userId, ConsentPurpose purpose, String noticeVersion) {
		boolean granted = repo.findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(userId, purpose)
				.map(Consent::isGranted).orElse(false);
		if (!granted) {
			repo.save(new Consent(userId, purpose, true, noticeVersion));
		}
	}

	@Transactional(readOnly = true)
	public List<ConsentDto.Response> current(Long userId) {
		return Arrays.stream(ConsentPurpose.values())
				.map(p -> toResponse(p, repo.findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(userId, p).orElse(null)))
				.toList();
	}

	/** True only when the user's latest recorded choice for this purpose is a withdrawal. */
	@Transactional(readOnly = true)
	public boolean isWithdrawn(Long userId, ConsentPurpose purpose) {
		return repo.findFirstByUserIdAndPurposeOrderByCreatedAtDescIdDesc(userId, purpose)
				.map(c -> !c.isGranted()).orElse(false);
	}

	@Transactional(readOnly = true)
	public List<Consent> history(Long userId) {
		return repo.findByUserIdOrderByCreatedAtDescIdDesc(userId);
	}

	private static ConsentDto.Response toResponse(ConsentPurpose purpose, Consent c) {
		return c == null
				? new ConsentDto.Response(purpose, null, null, null)
				: new ConsentDto.Response(purpose, c.isGranted(), c.getNoticeVersion(), c.getCreatedAt());
	}
}
