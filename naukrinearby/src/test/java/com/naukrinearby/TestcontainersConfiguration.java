package com.naukrinearby;

import java.nio.file.Path;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	ElasticsearchContainer elasticsearchContainer() {
		return new ElasticsearchContainer(
				DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.0"))
				.withEnv("xpack.security.enabled", "false")
				.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Build the PostGIS + pgvector image so Flyway migrations (CREATE EXTENSION
		// postgis / vector) succeed against the same database used in production.
		String image = new ImageFromDockerfile("naukrinearby/postgres-test:16", false)
				.withFileFromPath("Dockerfile", Path.of("docker/postgres/Dockerfile"))
				.get();
		return new PostgreSQLContainer(
				DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

}
