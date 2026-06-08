package com.naukrinearby.service.otp;

/**
 * Provider-agnostic OTP abstraction. The MVP ships a deterministic stub; a real Twilio Verify
 * implementation can be added behind this interface and selected via {@code naukri.otp.provider}.
 */
public interface OtpProvider {

	void send(String phone);

	boolean verify(String phone, String code);

	String providerId();
}
