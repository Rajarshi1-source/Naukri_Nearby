package com.naukrinearby.service.otp;

import com.naukrinearby.config.OtpProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic dev OTP provider: never calls an external service, always accepts the configured
 * dev code. Selected by {@code naukri.otp.provider=stub} (the default).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naukri.otp.provider", havingValue = "stub", matchIfMissing = true)
public class StubOtpProvider implements OtpProvider {

	private final OtpProperties props;

	@Override
	public void send(String phone) {
		log.info("[OTP stub] code for {} is {}", mask(phone), props.devCode());
	}

	@Override
	public boolean verify(String phone, String code) {
		return props.devCode().equals(code);
	}

	@Override
	public String providerId() {
		return "stub";
	}

	private static String mask(String phone) {
		if (phone == null || phone.length() < 4) {
			return "****";
		}
		return phone.substring(0, 2) + "XXXX" + phone.substring(phone.length() - 2);
	}
}
