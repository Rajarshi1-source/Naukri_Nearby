package com.naukrinearby.config;

import io.minio.MinioClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

	@Bean
	MinioClient minioClient(StorageProperties props) {
		return MinioClient.builder()
				.endpoint(props.endpoint())
				.credentials(props.accessKey(), props.secretKey())
				.build();
	}
}
