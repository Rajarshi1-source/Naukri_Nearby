package com.naukrinearby;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	@SuppressWarnings("resource")
	ElasticsearchContainer elasticsearchContainer() {
		return new ElasticsearchContainer(
				DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.2.8"))
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
	@SuppressWarnings("resource")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

	@Bean
	@SuppressWarnings("resource")
	GenericContainer<?> minioContainer() {
		return new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
				.withExposedPorts(9000)
				.withEnv("MINIO_ROOT_USER", "minioadmin")
				.withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
				.withCommand("server", "/data")
				.waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000)
						.withStartupTimeout(Duration.ofSeconds(60)));
	}

	/** MinIO has no @ServiceConnection support, so wire its endpoint/credentials dynamically. */
	@Bean
	DynamicPropertyRegistrar minioProperties(GenericContainer<?> minioContainer) {
		return registry -> {
			registry.add("naukri.storage.endpoint",
					() -> "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000));
			registry.add("naukri.storage.access-key", () -> "minioadmin");
			registry.add("naukri.storage.secret-key", () -> "minioadmin");
		};
	}

}
