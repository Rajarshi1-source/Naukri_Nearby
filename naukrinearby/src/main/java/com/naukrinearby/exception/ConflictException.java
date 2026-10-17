package com.naukrinearby.exception;

/** The request conflicts with existing state (for example, a second report of the same job). Maps to 409. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
