package com.naukrinearby.repository;

import java.util.List;
import java.util.Optional;

import com.naukrinearby.model.entity.CandidateProfile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

	Optional<CandidateProfile> findByUserId(Long userId);

	/** Writes the pgvector embedding via a text->vector cast (column is not JPA-mapped). */
	@Modifying
	@Query(value = "UPDATE candidate_profiles SET skill_embedding = CAST(:vec AS vector) WHERE user_id = :userId",
			nativeQuery = true)
	int updateSkillEmbedding(@Param("userId") Long userId, @Param("vec") String vec);

	/**
	 * Notification matching (master plan §8.3, query 3): candidates within their preferred radius of
	 * the job whose skill vector is similar enough. Returns rows of
	 * [user_id, name, phone, preferred_language, similarity, distance_km].
	 */
	@Query(value = """
			SELECT cp.user_id, cp.name, cp.phone, u.preferred_language,
			       1 - (cp.skill_embedding <=> (SELECT requirement_vector FROM jobs WHERE id = :jobId)) AS similarity,
			       ST_Distance(cp.location, (SELECT location FROM jobs WHERE id = :jobId)) / 1000 AS distance_km
			FROM candidate_profiles cp
			JOIN users u ON cp.user_id = u.id
			JOIN notification_preferences np ON np.user_id = u.id
			WHERE np.is_active = TRUE
			  AND cp.skill_embedding IS NOT NULL
			  AND cp.location IS NOT NULL
			  AND (SELECT requirement_vector FROM jobs WHERE id = :jobId) IS NOT NULL
			  AND ST_DWithin(cp.location, (SELECT location FROM jobs WHERE id = :jobId), np.radius_km * 1000)
			  AND (1 - (cp.skill_embedding <=> (SELECT requirement_vector FROM jobs WHERE id = :jobId))) > :threshold
			ORDER BY similarity DESC
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findMatchingCandidates(@Param("jobId") Long jobId,
			@Param("threshold") double threshold, @Param("limit") int limit);
}
