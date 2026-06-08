package com.naukrinearby.repository;

import java.util.List;
import java.util.Optional;

import com.naukrinearby.model.entity.Job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

	@EntityGraph(attributePaths = "employer")
	Optional<Job> findBySlug(String slug);

	@EntityGraph(attributePaths = "employer")
	Page<Job> findByEmployer_IdOrderByCreatedAtDesc(Long employerId, Pageable pageable);

	/** PostGIS hyperlocal search — also the Elasticsearch-down fallback (master plan §9.6). */
	@Query(value = """
			SELECT j.* FROM jobs j
			WHERE j.status = 'ACTIVE'
			  AND j.location IS NOT NULL
			  AND ST_DWithin(j.location, ST_MakePoint(:lng, :lat)::geography, :radiusM)
			ORDER BY ST_Distance(j.location, ST_MakePoint(:lng, :lat)::geography) ASC
			LIMIT :limit
			""", nativeQuery = true)
	List<Job> findWithinRadius(@Param("lat") double lat, @Param("lng") double lng,
			@Param("radiusM") double radiusM, @Param("limit") int limit);

	/** Distance in km from a point to a job (used to annotate fallback results). */
	@Query(value = "SELECT ST_Distance(location, ST_MakePoint(:lng, :lat)::geography) / 1000 "
			+ "FROM jobs WHERE id = :jobId", nativeQuery = true)
	Double distanceKm(@Param("jobId") Long jobId, @Param("lat") double lat, @Param("lng") double lng);

	/** Recommended jobs for a candidate (master plan §8.3, query 2): rows of [job_id, similarity, distance_km]. */
	@Query(value = """
			SELECT j.id, 1 - (cp.skill_embedding <=> j.requirement_vector) AS similarity,
			       ST_Distance(j.location, cp.location) / 1000 AS distance_km
			FROM jobs j, candidate_profiles cp
			WHERE cp.user_id = :userId AND j.status = 'ACTIVE'
			  AND j.requirement_vector IS NOT NULL AND cp.skill_embedding IS NOT NULL
			  AND j.location IS NOT NULL AND cp.location IS NOT NULL
			  AND ST_DWithin(j.location, cp.location, cp.preferred_radius_km * 1000)
			ORDER BY similarity DESC
			LIMIT :limit
			""", nativeQuery = true)
	List<Object[]> findRecommendedForCandidate(@Param("userId") Long userId, @Param("limit") int limit);

	/** Writes the pgvector requirement vector via a text->vector cast (column is not JPA-mapped). */
	@Modifying
	@Query(value = "UPDATE jobs SET requirement_vector = CAST(:vec AS vector) WHERE id = :jobId",
			nativeQuery = true)
	int updateRequirementVector(@Param("jobId") Long jobId, @Param("vec") String vec);

	@Modifying
	@Query("UPDATE Job j SET j.applicationCount = j.applicationCount + 1 WHERE j.id = :jobId")
	int incrementApplicationCount(@Param("jobId") Long jobId);
}
