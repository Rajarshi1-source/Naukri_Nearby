package com.naukrinearby.exception;

/** Thrown for service-layer validation failures (e.g. coordinates outside India). */
public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}
}
