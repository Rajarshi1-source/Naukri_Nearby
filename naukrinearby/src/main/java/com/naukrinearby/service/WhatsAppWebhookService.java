package com.naukrinearby.service;

import java.time.Instant;
import java.util.Map;

import com.naukrinearby.config.TwilioProperties;
import com.naukrinearby.model.entity.NotificationLog;
import com.naukrinearby.repository.NotificationLogRepository;
import com.naukrinearby.util.TwilioSignatureValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles Twilio status callbacks: verifies the signature and idempotently updates delivery state. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

	private final NotificationLogRepository notificationLogRepo;
	private final TwilioProperties twilioProps;

	public boolean verifySignature(String url, Map<String, String> params, String signature) {
		String token = twilioProps.authToken();
		// In stub mode (no auth token configured) we accept callbacks for local testing.
		if (token == null || token.isBlank() || "stub".equals(twilioProps.provider())) {
			return true;
		}
		return TwilioSignatureValidator.isValid(token, url, params, signature);
	}

	/** Idempotent on MessageSid — repeated callbacks for the same SID just refresh timestamps. */
	@Transactional
	public void handleStatusCallback(Map<String, String> params) {
		String sid = params.get("MessageSid");
		String status = params.get("MessageStatus");
		if (sid == null) {
			return;
		}
		notificationLogRepo.findByMessageSid(sid).ifPresent(log -> applyStatus(log, status));
	}

	private void applyStatus(NotificationLog log, String status) {
		if (status == null) {
			return;
		}
		log.setStatus(status.toUpperCase());
		switch (status.toLowerCase()) {
			case "delivered" -> log.setDeliveredAt(Instant.now());
			case "read" -> log.setReadAt(Instant.now());
			default -> {
			}
		}
		notificationLogRepo.save(log);
	}
}
