package com.naukrinearby.exception;

/** An external provider (e.g. Bhashini speech) failed after retries or its circuit breaker is open. */
public class ServiceUnavailableException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ServiceUnavailableException(String message) {
		super(message);
	}
}
