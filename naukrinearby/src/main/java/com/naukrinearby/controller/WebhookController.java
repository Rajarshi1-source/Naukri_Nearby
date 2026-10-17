package com.naukrinearby.controller;

import java.util.Map;

import com.naukrinearby.service.WhatsAppConversationService;
import com.naukrinearby.service.WhatsAppWebhookService;
import com.naukrinearby.util.TwiML;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebhookController {

	private final WhatsAppWebhookService webhookService;
	private final WhatsAppConversationService conversationService;

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

	/**
	 * Inbound WhatsApp message (Twilio messaging webhook) — drives the WhatsApp-first apply chat.
	 * Replies with TwiML so Twilio sends the response message back to the candidate.
	 */
	@PostMapping(value = "/api/webhooks/twilio/inbound", produces = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity<String> inbound(HttpServletRequest request,
			@RequestParam Map<String, String> params,
			@RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
		String url = request.getRequestURL().toString();
		if (!webhookService.verifySignature(url, params, signature)) {
			return ResponseEntity.status(403).build();
		}
		String reply = conversationService.handleInbound(params.get("From"), params.get("Body"));
		return ResponseEntity.ok()
				.contentType(TwiML.CONTENT_TYPE)
				.body(TwiML.response().message(reply).build());
	}
}
