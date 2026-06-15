package com.naukrinearby;

import java.time.Duration;
import java.util.List;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.naukrinearby.config.SearchProperties;
import com.naukrinearby.model.dto.search.SearchQuery;
import com.naukrinearby.model.dto.search.SearchResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.repository.JobRepository;
import com.naukrinearby.service.SearchService;
import com.naukrinearby.service.search.HybridSearchStrategy;
import com.naukrinearby.service.search.SearchRankingStrategy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test: when Elasticsearch errors, search degrades to the PostGIS path (no containers needed). */
class SearchServiceFallbackTest {

	@Test
	@SuppressWarnings("unchecked")
	void elasticsearchFailure_fallsBackToPostgis() throws Exception {
		ElasticsearchClient es = mock(ElasticsearchClient.class);
		when(es.search(any(java.util.function.Function.class), any()))
				.thenThrow(new RuntimeException("ES down"));

		JobRepository jobRepo = mock(JobRepository.class);
		Job job = mock(Job.class);
		when(job.getId()).thenReturn(1L);
		when(job.getTitle()).thenReturn("Plumber");
		when(job.getSlug()).thenReturn("plumber-1");
		when(job.getCity()).thenReturn("Lucknow");
		when(jobRepo.findWithinRadius(anyDouble(), anyDouble(), anyDouble(), anyInt()))
				.thenReturn(List.of(job));

		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.get(any())).thenReturn(null);

		SearchProperties props = new SearchProperties("jobs", Duration.ofMinutes(5), 20, "geo", 50, 60);
		SearchService service = new SearchService(es, jobRepo, mock(SearchRankingStrategy.class),
				mock(HybridSearchStrategy.class), props, redis, new JsonMapper(),
				new SimpleMeterRegistry());

		SearchResponse response = service.search(new SearchQuery("plumber", 26.84, 80.94, 10, null, 20));

		assertThat(response.fellBackToPostgis()).isTrue();
		assertThat(response.source()).isEqualTo("postgis");
		assertThat(response.results()).hasSize(1);
		assertThat(response.results().get(0).title()).isEqualTo("Plumber");
	}
}
