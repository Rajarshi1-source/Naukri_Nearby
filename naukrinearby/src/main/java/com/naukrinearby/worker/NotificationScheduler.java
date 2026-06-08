package com.naukrinearby.worker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukrinearby.config.NotificationProperties;
import com.naukrinearby.service.NotificationService;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis Streams consumer for the WhatsApp alert queue. Reads with a consumer group, delegates each
 * record to {@link NotificationService#process}, acks on success, and routes poison messages to a DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

	private final StringRedisTemplate redis;
	private final NotificationService notificationService;
	private final NotificationProperties props;

	@PostConstruct
	void ensureConsumerGroup() {
		try {
			redis.opsForStream().createGroup(props.stream(), ReadOffset.from("0-0"), props.consumerGroup());
			log.info("Created Redis stream consumer group '{}' on '{}'", props.consumerGroup(), props.stream());
		}
		catch (Exception ex) {
			// BUSYGROUP — already exists. Safe to ignore.
			log.debug("Consumer group init: {}", ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Scheduled(fixedDelay = 1000)
	public void poll() {
		try {
			List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
					Consumer.from(props.consumerGroup(), props.consumerName()),
					StreamReadOptions.empty().count(20),
					StreamOffset.create(props.stream(), ReadOffset.lastConsumed()));
			if (records == null || records.isEmpty()) {
				return;
			}
			for (MapRecord<String, Object, Object> record : records) {
				Map<String, String> fields = toStringMap(record.getValue());
				boolean done;
				try {
					done = notificationService.process(fields);
				}
				catch (Exception ex) {
					log.warn("Notification processing threw: {}", ex.getMessage());
					done = false;
				}
				if (!done) {
					redis.opsForStream().add(props.dlqStream(), fields);
				}
				redis.opsForStream().acknowledge(props.stream(), props.consumerGroup(), record.getId());
			}
		}
		catch (Exception ex) {
			log.warn("Notification poll failed: {}", ex.getMessage());
		}
	}

	private static Map<String, String> toStringMap(Map<Object, Object> raw) {
		Map<String, String> out = new HashMap<>();
		raw.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
		return out;
	}
}
