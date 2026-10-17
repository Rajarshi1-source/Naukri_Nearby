package com.naukrinearby.controller;

import com.naukrinearby.model.dto.EmployerProfileDto;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.EmployerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Employer profile and GSTIN-based business verification (ADR 0007). */
@RestController
@RequestMapping("/api/employer/profile")
@PreAuthorize("hasRole('EMPLOYER')")
@RequiredArgsConstructor
public class EmployerController {

	private final EmployerService employerService;

	@GetMapping
	public EmployerProfileDto.Response get(@AuthenticationPrincipal AuthPrincipal principal) {
		return EmployerProfileDto.Response.from(employerService.get(principal.id()));
	}

	@PutMapping
	public EmployerProfileDto.Response update(@Valid @RequestBody EmployerProfileDto.Request req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return EmployerProfileDto.Response.from(employerService.update(principal.id(), req));
	}
}
