package com.naukrinearby.exception;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Single source of RFC 9457 ProblemDetail responses (security-and-api.md §B.2). */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/** Unmapped URL (e.g. /actuator/** on the public port): a 404, not the catch-all 500. */
	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail handleNoResource(NoResourceFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such endpoint");
	}

	@ExceptionHandler({ DuplicateApplicationException.class, ConflictException.class })
	ProblemDetail handleDuplicate(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(ForbiddenException.class)
	ProblemDetail handleForbidden(ForbiddenException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	/**
	 * {@code @PreAuthorize} denials surface as {@link AccessDeniedException} inside the MVC call, so
	 * without this they would fall through to the catch-all below and become a 500.
	 */
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
	}

	@ExceptionHandler(RateLimitException.class)
	ProblemDetail handleRateLimit(RateLimitException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
	}

	@ExceptionHandler(ServiceUnavailableException.class)
	ProblemDetail handleUnavailable(ServiceUnavailableException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	/** Upload over {@code spring.servlet.multipart.max-file-size}: a 413, not the catch-all 500. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Upload is too large");
	}

	/** A multipart body that cannot be parsed: a 400, not the catch-all 500. */
	@ExceptionHandler(MultipartException.class)
	ProblemDetail handleBadMultipart(MultipartException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed multipart request");
	}

	@ExceptionHandler(ResumeParseException.class)
	ProblemDetail handleParse(ResumeParseException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	@ExceptionHandler({ ValidationException.class, IllegalArgumentException.class })
	ProblemDetail handleBadRequest(RuntimeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		pd.setTitle("Validation failed");
		List<String> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.toList();
		pd.setProperty("errors", errors);
		return pd;
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleGeneric(Exception ex) {
		// Spring MVC's own errors (415, 405, missing parameter or part, ...) already carry the right status.
		if (ex instanceof ErrorResponse error) {
			return error.getBody();
		}
		log.error("Unhandled exception", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"An unexpected error occurred");
	}
}
