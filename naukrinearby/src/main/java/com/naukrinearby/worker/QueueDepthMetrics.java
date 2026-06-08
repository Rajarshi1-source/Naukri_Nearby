package com.naukrinearby.worker;

import com.naukrinearby.config.NotificationProperties;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes Redis Stream depth as Micrometer gauges so Prometheus/Grafana can alert on a backed-up
 * notification queue or a growing DLQ (master plan §16 observability). Gauges poll Redis lazily when
 * the registry scrapes, so there's no extra scheduled load.
 */
@Component
@RequiredArgsConstructor
public class QueueDepthMetrics {

	private final MeterRegistry meterRegistry;
	private final StringRedisTemplate redis;
	private final NotificationProperties props;

	@PostConstruct
	void register() {
		meterRegistry.gauge("notification_queue_depth", this, m -> m.streamSize(props.stream()));
		meterRegistry.gauge("notification_dlq_depth", this, m -> m.streamSize(props.dlqStream()));
	}

	private double streamSize(String stream) {
		try {
			Long size = redis.opsForStream().size(stream);
			return size == null ? 0.0 : size.doubleValue();
		}
		catch (Exception ex) {
			return 0.0;
		}
	}
}
