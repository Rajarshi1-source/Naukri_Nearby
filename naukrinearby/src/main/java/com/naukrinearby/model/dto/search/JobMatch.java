package com.naukrinearby.model.dto.search;

import com.naukrinearby.model.dto.job.JobResponse;

public record JobMatch(JobResponse job, double similarity, Double distanceKm) {
}
