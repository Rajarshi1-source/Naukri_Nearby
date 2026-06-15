package com.naukrinearby.exception;

/** Thrown for service-layer validation failures (e.g. coordinates outside India). */
public class ValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ValidationException(String message) {
		super(message);
	}
}
