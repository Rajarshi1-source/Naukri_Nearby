package com.naukrinearby.service.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack et al.): combines several ranked lists into one without needing
 * comparable raw scores. Each item gets {@code sum(1 / (k + rank))} across the lists it appears in
 * (rank is 1-based); {@code k} (typically 60) damps the influence of top ranks so lists that agree on
 * mid-ranked items still surface. Used to fuse the ES BM25 leg with the pgvector cosine leg (G4).
 */
public final class ReciprocalRankFusion {

	private ReciprocalRankFusion() {
	}

	/** Fuses ranked id lists and returns ids ordered by descending fused score. */
	public static List<Long> fuse(int k, List<List<Long>> rankedLists) {
		Map<Long, Double> scores = new LinkedHashMap<>();
		for (List<Long> list : rankedLists) {
			for (int rank = 0; rank < list.size(); rank++) {
				Long id = list.get(rank);
				scores.merge(id, 1.0 / (k + rank + 1), Double::sum);
			}
		}
		return scores.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.map(Map.Entry::getKey)
				.toList();
	}
}
