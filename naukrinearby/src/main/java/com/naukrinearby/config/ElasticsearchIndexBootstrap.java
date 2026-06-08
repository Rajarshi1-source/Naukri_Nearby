package com.naukrinearby.config;

import java.io.StringReader;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the jobs index (geo_point + Hindi analyzer, master plan §8.4) behind a versioned name with
 * a stable read/write alias, on startup, if absent. Tolerates ES being down — search degrades to
 * PostGIS until ES recovers and the next app start (or a manual call) creates the index.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchIndexBootstrap {

	private final ElasticsearchClient es;
	private final SearchProperties props;

	private static final String INDEX_TEMPLATE = """
			{
			  "aliases": { "%s": {} },
			  "mappings": {
			    "properties": {
			      "id":          { "type": "long" },
			      "title":       { "type": "text", "analyzer": "hindi",
			                       "fields": { "std": { "type": "text", "analyzer": "standard" },
			                                   "keyword": { "type": "keyword" } } },
			      "description": { "type": "text", "analyzer": "hindi" },
			      "category":    { "type": "keyword" },
			      "skills":      { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
			      "city":        { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
			      "state":       { "type": "keyword" },
			      "pincode":     { "type": "keyword" },
			      "salary_min":  { "type": "integer" },
			      "salary_max":  { "type": "integer" },
			      "status":      { "type": "keyword" },
			      "company_name":{ "type": "text", "fields": { "keyword": { "type": "keyword" } } },
			      "location":    { "type": "geo_point" },
			      "created_at":  { "type": "date" },
			      "slug":        { "type": "keyword" }
			    }
			  }
			}
			""";

	@EventListener(ApplicationReadyEvent.class)
	public void bootstrap() {
		String alias = props.index();
		String indexName = alias + "_v1";
		try {
			boolean exists = es.indices().exists(e -> e.index(alias)).value();
			if (exists) {
				log.info("Elasticsearch alias '{}' already present", alias);
				return;
			}
			String body = INDEX_TEMPLATE.formatted(alias);
			es.indices().create(c -> c.index(indexName).withJson(new StringReader(body)));
			log.info("Created Elasticsearch index '{}' with alias '{}'", indexName, alias);
		}
		catch (Exception ex) {
			log.warn("Could not bootstrap Elasticsearch index (search will use PostGIS fallback): {}",
					ex.getMessage());
		}
	}
}
