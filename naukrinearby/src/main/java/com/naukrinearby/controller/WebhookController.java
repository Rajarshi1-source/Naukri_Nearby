package com.naukrinearby.controller;

import java.util.Map;

import com.naukrinearby.service.WhatsAppWebhookService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebhookController {

	private final WhatsAppWebhookService webhookService;

	/** Twilio WhatsApp status callback. Public route, but protected by signature verification. */
	@PostMapping("/api/webhooks/twilio")
	public ResponseEntity<Void> twilio(HttpServletRequest request,
			@RequestParam Map<String, String> params,
			@RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
		String url = request.getRequestURL().toString();
		if (!webhookService.verifySignature(url, params, signature)) {
			return ResponseEntity.status(403).build();
		}
		webhookService.handleStatusCallback(params);
		return ResponseEntity.ok().build();
	}
}
