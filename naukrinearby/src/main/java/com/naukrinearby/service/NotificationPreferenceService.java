package com.naukrinearby.service;

import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.model.entity.NotificationPreference;
import com.naukrinearby.model.enums.NotificationChannel;
import com.naukrinearby.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository prefRepo;

	@Transactional
	public NotificationPreference getOrCreate(Long userId) {
		return prefRepo.findByUserId(userId).orElseGet(() -> {
			NotificationPreference p = new NotificationPreference();
			p.setUserId(userId);
			return prefRepo.save(p);
		});
	}

	@Transactional
	public NotificationPreference update(Long userId, NotificationPreferenceDto req) {
		NotificationPreference p = getOrCreate(userId);
		if (req.channel() != null) {
			p.setChannel(NotificationChannel.valueOf(req.channel().toUpperCase()));
		}
		if (req.language() != null) {
			p.setLanguage(req.language());
		}
		if (req.radiusKm() != null) {
			p.setRadiusKm(req.radiusKm());
		}
		if (req.categories() != null) {
			p.setCategories(req.categories().toArray(new String[0]));
		}
		if (req.frequency() != null) {
			p.setFrequency(req.frequency());
		}
		if (req.active() != null) {
			p.setActive(req.active());
		}
		return prefRepo.save(p);
	}
}
