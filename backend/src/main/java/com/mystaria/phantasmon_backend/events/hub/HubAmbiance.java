package com.mystaria.phantasmon_backend.events.hub;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The Showtime ambiance laid on a hub (2026-11-19): everyone in it sees it, battle or not. */
@Entity
@Table(name = "event_hub_ambiances")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HubAmbiance {

	@Id
	@Column(name = "hub_uuid")
	private UUID hubUuid;

	@Setter
	@Column(nullable = false, length = 130)
	private String ambiance;

	@Setter
	@Column(name = "updated_by")
	private UUID updatedBy;

	@Setter
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public HubAmbiance(UUID hubUuid, String ambiance, UUID updatedBy, Instant updatedAt) {
		this.hubUuid = hubUuid;
		this.ambiance = ambiance;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}
}
