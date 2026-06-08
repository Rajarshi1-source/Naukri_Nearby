package com.naukrinearby;

import org.springframework.boot.SpringApplication;

public class TestNaukriNearbyApplication {

	public static void main(String[] args) {
		SpringApplication.from(NaukriNearbyApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
