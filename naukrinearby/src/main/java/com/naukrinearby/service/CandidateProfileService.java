package com.naukrinearby.service;

import java.time.Instant;
import java.util.List;

import com.naukrinearby.model.dto.ProfileUpdateRequest;
import com.naukrinearby.model.dto.ResumeParseResult;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.repository.CandidateProfileRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CandidateProfileService {

	private final CandidateProfileRepository profileRepo;
	private final EmbeddingService embeddingService;
	private final GeocodingService geocodingService;

	/** Upserts the candidate's profile from an extracted resume, then writes the skill embedding. */
	@Transactional
	public void applyParsedResume(Long userId, ResumeParseResult r, String fileKey) {
		CandidateProfile profile = profileRepo.findByUserId(userId).orElseGet(() -> {
			CandidateProfile p = new CandidateProfile();
			p.setUserId(userId);
			return p;
		});

		if (r.getName() != null) {
			profile.setName(r.getName());
		}
		if (r.getPhone() != null) {
			profile.setPhone(r.getPhone());
		}
		if (r.getEmail() != null) {
			profile.setEmail(r.getEmail());
		}
		if (r.getCity() != null) {
			profile.setCity(r.getCity());
		}
		if (r.getState() != null) {
			profile.setState(r.getState());
		}
		profile.setSkills(r.getSkills() == null ? new String[0] : r.getSkills().toArray(new String[0]));
		profile.setExperience(mapExperience(r.getExperience()));
		profile.setEducation(mapEducation(r.getEducation()));
		if (r.getLanguagesSpoken() != null && !r.getLanguagesSpoken().isEmpty()) {
			profile.setLanguagesSpoken(r.getLanguagesSpoken().toArray(new String[0]));
		}
		profile.setTotalExperienceMonths(r.getTotalExperienceMonths() == null ? 0 : r.getTotalExperienceMonths());
		profile.setResumeFileKey(fileKey);
		profile.setResumeParsedAt(Instant.now());
		profile.setProfileCompleteness(completeness(profile));

		profile = profileRepo.save(profile);

		String literal = embeddingService.toVectorLiteral(skillText(r));
		if (literal != null) {
			profileRepo.updateSkillEmbedding(profile.getUserId(), literal);
		}
	}

	@Transactional(readOnly = true)
	public CandidateProfile getByUserId(Long userId) {
		return profileRepo.findByUserId(userId).orElse(null);
	}

	/** Applies a manual profile edit (creates the profile if none exists). Skills stay resume-owned. */
	@Transactional
	public CandidateProfile updateProfile(Long userId, ProfileUpdateRequest req) {
		CandidateProfile profile = profileRepo.findByUserId(userId).orElseGet(() -> {
			CandidateProfile p = new CandidateProfile();
			p.setUserId(userId);
			return p;
		});
		if (req.name() != null) {
			profile.setName(req.name());
		}
		if (req.email() != null) {
			profile.setEmail(req.email());
		}
		if (req.city() != null) {
			profile.setCity(req.city());
		}
		if (req.state() != null) {
			profile.setState(req.state());
		}
		if (req.lat() != null && req.lng() != null) {
			profile.setLocation(geocodingService.validateAndBuild(req.lat(), req.lng()));
		}
		if (req.preferredRadiusKm() != null) {
			profile.setPreferredRadiusKm(geocodingService.clampRadiusKm(req.preferredRadiusKm()));
		}
		if (req.preferredCategories() != null) {
			profile.setPreferredCategories(req.preferredCategories().toArray(new String[0]));
		}
		if (req.languagesSpoken() != null && !req.languagesSpoken().isEmpty()) {
			profile.setLanguagesSpoken(req.languagesSpoken().toArray(new String[0]));
		}
		profile.setProfileCompleteness(completeness(profile));
		return profileRepo.save(profile);
	}

	private static String skillText(ResumeParseResult r) {
		String skills = r.getSkills() == null ? "" : String.join(", ", r.getSkills());
		String city = r.getCity() == null ? "" : r.getCity();
		return skills + ". " + city;
	}

	private static List<CandidateProfile.ExperienceItem> mapExperience(List<ResumeParseResult.Experience> src) {
		if (src == null) {
			return List.of();
		}
		return src.stream().map(e -> {
			CandidateProfile.ExperienceItem item = new CandidateProfile.ExperienceItem();
			item.setTitle(e.getTitle());
			item.setCompany(e.getCompany());
			item.setMonths(e.getMonths());
			return item;
		}).toList();
	}

	private static List<CandidateProfile.EducationItem> mapEducation(List<ResumeParseResult.Education> src) {
		if (src == null) {
			return List.of();
		}
		return src.stream().map(e -> {
			CandidateProfile.EducationItem item = new CandidateProfile.EducationItem();
			item.setDegree(e.getDegree());
			item.setInstitution(e.getInstitution());
			item.setYear(e.getYear());
			return item;
		}).toList();
	}

	private static int completeness(CandidateProfile p) {
		int score = 0;
		if (p.getName() != null) {
			score += 20;
		}
		if (p.getPhone() != null) {
			score += 15;
		}
		if (p.getCity() != null) {
			score += 15;
		}
		if (p.getSkills() != null && p.getSkills().length > 0) {
			score += 30;
		}
		if (p.getExperience() != null && !p.getExperience().isEmpty()) {
			score += 20;
		}
		return Math.min(score, 100);
	}
}
