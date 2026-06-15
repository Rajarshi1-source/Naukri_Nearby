package com.naukrinearby.exception;

public class DuplicateApplicationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public DuplicateApplicationException(String message) {
		super(message);
	}
}
