package com.naukrinearby.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "employers")
@Getter
@Setter
@NoArgsConstructor
public class Employer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", unique = true)
	private Long userId;

	@Column(name = "company_name", nullable = false)
	private String companyName;

	@Column(name = "company_type")
	private String companyType;

	@Column(name = "gst_number")
	private String gstNumber;

	private String address;

	private String city;

	private String state;

	@Column(columnDefinition = "geography(Point,4326)")
	private Point location;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;
}
