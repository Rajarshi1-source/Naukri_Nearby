package com.naukrinearby.exception;

public class ResumeParseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ResumeParseException(String message) {
		super(message);
	}

	public ResumeParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
