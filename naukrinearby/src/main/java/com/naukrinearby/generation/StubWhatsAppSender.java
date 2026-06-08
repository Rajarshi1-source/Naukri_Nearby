package com.naukrinearby.generation;

import java.util.UUID;

import com.naukrinearby.util.PiiRedactor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * WhatsApp send stub: logs and returns a fake MessageSid instead of calling Twilio. Selected by
 * {@code naukri.twilio.provider=stub} (default). A real Twilio impl can replace it behind this interface.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.twilio.provider", havingValue = "stub", matchIfMissing = true)
public class StubWhatsAppSender implements WhatsAppSender {

	@Override
	public String send(String toPhone, String body) {
		String sid = "STUB-" + UUID.randomUUID();
		log.info("[WhatsApp stub] -> {} sid={} : {}", PiiRedactor.maskPhone(toPhone), sid,
				body == null ? "" : PiiRedactor.redact(body.replaceAll("\\s+", " ").trim()));
		return sid;
	}

	@Override
	public String providerId() {
		return "stub";
	}
}
