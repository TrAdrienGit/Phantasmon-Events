package com.mystaria.phantasmon_backend.events.hub;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HubAmbianceRepository extends JpaRepository<HubAmbiance, UUID> {
}
