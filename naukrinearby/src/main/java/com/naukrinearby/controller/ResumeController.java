package com.naukrinearby.controller;

import java.util.Map;

import com.naukrinearby.model.dto.ResumeUploadResponse;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.ResumeParserService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

	private final ResumeParserService resumeParserService;

	/** Uploads a resume and parses it off-thread; returns 202 with a pollable resumeId. */
	@PostMapping("/upload")
	@PreAuthorize("hasRole('CANDIDATE')")
	public ResponseEntity<ResumeUploadResponse> upload(@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal AuthPrincipal principal) {
		String resumeId = resumeParserService.store(principal.id(), file);
		resumeParserService.parseAsync(resumeId);
		return ResponseEntity.accepted().body(new ResumeUploadResponse(resumeId, "PENDING"));
	}

	@GetMapping("/{resumeId}/parse-status")
	@PreAuthorize("hasRole('CANDIDATE')")
	public Map<String, String> parseStatus(@PathVariable String resumeId) {
		return Map.of("resumeId", resumeId, "status", resumeParserService.status(resumeId));
	}
}
