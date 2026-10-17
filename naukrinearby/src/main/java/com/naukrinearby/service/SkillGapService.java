package com.naukrinearby.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.naukrinearby.model.dto.SkillGapResponse;
import com.naukrinearby.model.dto.SkillGapResponse.Portal;
import com.naukrinearby.model.dto.SkillGapResponse.Suggestion;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.service.search.QueryNormalizer;
import com.naukrinearby.util.ClasspathLines;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skill-gap recommendations (supplement G7): compares a job's required skills with the candidate's
 * profile after normalizing both through the Hinglish lexicon, and points each missing skill at free,
 * official learning portals from {@code resources/trust/learning-portals.tsv}. No course URLs are
 * invented — each suggestion is a portal plus the term to search for.
 */
@Service
public class SkillGapService {

	private static final int MAX_PORTALS_PER_SKILL = 3;

	private final JobService jobService;
	private final CandidateProfileRepository profileRepo;
	private final QueryNormalizer normalizer;
	private final List<PortalEntry> portals;

	public SkillGapService(JobService jobService, CandidateProfileRepository profileRepo, QueryNormalizer normalizer) {
		this.jobService = jobService;
		this.profileRepo = profileRepo;
		this.normalizer = normalizer;
		this.portals = loadPortals();
	}

	@Transactional(readOnly = true)
	public SkillGapResponse forCandidate(Long userId, Long jobId) {
		Job job = jobService.getPublicJob(jobId);
		String[] candidateSkills = profileRepo.findByUserId(userId)
				.map(CandidateProfile::getSkills)
				.orElse(new String[0]);
		return compare(jobId, job.getSkillsRequired(), candidateSkills);
	}

	SkillGapResponse compare(Long jobId, String[] required, String[] have) {
		Set<String> haveCanonical = new HashSet<>();
		for (String s : have == null ? new String[0] : have) {
			haveCanonical.add(normalizer.canonicalSkill(s));
		}
		List<String> matched = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String skill : required == null ? new String[0] : required) {
			if (skill == null || skill.isBlank()) {
				continue;
			}
			(haveCanonical.contains(normalizer.canonicalSkill(skill)) ? matched : missing).add(skill.strip());
		}
		int total = matched.size() + missing.size();
		int percent = total == 0 ? 100 : Math.round(100f * matched.size() / total);
		List<Suggestion> suggestions = missing.stream()
				.map(skill -> new Suggestion(skill, normalizer.canonicalSkill(skill), portalsFor(skill)))
				.toList();
		return new SkillGapResponse(jobId, matched, missing, percent, suggestions);
	}

	private List<Portal> portalsFor(String skill) {
		String key = normalizer.canonicalSkill(skill);
		List<Portal> specific = new ArrayList<>();
		List<Portal> general = new ArrayList<>();
		for (PortalEntry p : portals) {
			if (p.keywords().contains("*")) {
				general.add(p.portal());
			}
			else if (key.length() >= 3 && p.keywords().stream().anyMatch(k -> key.contains(k) || k.contains(key))) {
				specific.add(p.portal());
			}
		}
		specific.addAll(general);
		return specific.subList(0, Math.min(MAX_PORTALS_PER_SKILL, specific.size()));
	}

	private record PortalEntry(Portal portal, Set<String> keywords) {
	}

	private static List<PortalEntry> loadPortals() {
		List<PortalEntry> out = new ArrayList<>();
		for (String line : ClasspathLines.read("trust/learning-portals.tsv")) {
			String[] f = line.split("\t");
			if (f.length != 4) {
				continue;
			}
			Set<String> keywords = new HashSet<>();
			Arrays.stream(f[3].split(",")).map(k -> k.strip().toLowerCase(Locale.ROOT))
					.filter(k -> !k.isEmpty()).forEach(keywords::add);
			out.add(new PortalEntry(new Portal(f[0].strip(), f[1].strip(), f[2].strip()), keywords));
		}
		return List.copyOf(out);
	}
}
