package com.naukrinearby.controller;

import java.util.Map;

import com.naukrinearby.service.IvrService;
import com.naukrinearby.service.WhatsAppWebhookService;
import com.naukrinearby.util.TwiML;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Twilio Programmable Voice webhooks for voice-first apply (ADR 0008). Public routes, authenticated
 * by {@code X-Twilio-Signature} exactly like the WhatsApp webhooks; every reply is TwiML. The step is
 * a path segment (not a query parameter) so the signed URL is the plain request URL.
 */
@RestController
@RequiredArgsConstructor
public class IvrController {

	private final IvrService ivrService;
	private final WhatsAppWebhookService webhookService;

	@PostMapping(value = IvrService.BASE, produces = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity<String> incomingCall(HttpServletRequest request, @RequestParam Map<String, String> params,
			@RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
		if (!verified(request, params, signature)) {
			return ResponseEntity.status(403).build();
		}
		return twiml(ivrService.start(params.get("CallSid"), params.get("From")));
	}

	@PostMapping(value = IvrService.BASE + "/gather/{step}", produces = MediaType.APPLICATION_XML_VALUE)
	public ResponseEntity<String> gather(HttpServletRequest request, @PathVariable String step,
			@RequestParam Map<String, String> params,
			@RequestHeader(value = "X-Twilio-Signature", required = false) String signature) {
		if (!verified(request, params, signature)) {
			return ResponseEntity.status(403).build();
		}
		String callSid = params.get("CallSid");
		String digits = params.get("Digits");
		String speech = params.get("SpeechResult");
		String body = switch (step) {
			case "menu" -> ivrService.onMenu(callSid, digits, speech);
			case "job" -> ivrService.onJobNumber(callSid, digits, speech);
			case "confirm" -> ivrService.onConfirm(callSid, digits, speech);
			default -> null;
		};
		return body == null ? ResponseEntity.notFound().build() : twiml(body);
	}

	private boolean verified(HttpServletRequest request, Map<String, String> params, String signature) {
		return webhookService.verifySignature(request.getRequestURL().toString(), params, signature);
	}

	private static ResponseEntity<String> twiml(String body) {
		return ResponseEntity.ok().contentType(TwiML.CONTENT_TYPE).body(body);
	}
}
