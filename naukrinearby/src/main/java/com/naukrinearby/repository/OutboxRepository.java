package com.naukrinearby.repository;

import java.util.List;

import com.naukrinearby.model.entity.OutboxEvent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

	/**
	 * Pull a batch of unprocessed events, oldest first. {@code FOR UPDATE SKIP LOCKED} lets multiple
	 * sync workers run concurrently without double-processing (starter §1.3).
	 */
	@Query(value = """
			SELECT * FROM outbox_events
			WHERE status = 'PENDING'
			ORDER BY created_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OutboxEvent> fetchPendingBatch(@Param("limit") int limit);

	long countByStatus(String status);
}
