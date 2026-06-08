package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.notification")
public record NotificationProperties(
		int dailyLimit,
		double matchThreshold,
		String stream,
		String dlqStream,
		String consumerGroup,
		String consumerName) {

	public NotificationProperties {
		if (dailyLimit <= 0) {
			dailyLimit = 3;
		}
		if (matchThreshold <= 0) {
			matchThreshold = 0.6;
		}
		if (stream == null || stream.isBlank()) {
			stream = "notification-jobs";
		}
		if (dlqStream == null || dlqStream.isBlank()) {
			dlqStream = "notification-dlq";
		}
		if (consumerGroup == null || consumerGroup.isBlank()) {
			consumerGroup = "notifiers";
		}
		if (consumerName == null || consumerName.isBlank()) {
			consumerName = "worker-1";
		}
	}
}
