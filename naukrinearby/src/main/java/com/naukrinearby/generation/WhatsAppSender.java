package com.naukrinearby.generation;

/** Sends a WhatsApp message and returns the provider message id (Twilio MessageSid). */
public interface WhatsAppSender {

	String send(String toPhone, String body);

	String providerId();
}
