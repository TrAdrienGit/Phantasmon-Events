package com.mystaria.phantasmon_backend.events.trahison;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.events.MirrorHubs;
import com.mystaria.phantasmon_backend.hub.Hub;
import com.mystaria.phantasmon_backend.hub.HubRepository;
import com.mystaria.phantasmon_backend.hub.HubService;

/**
 * The real {@link HubGate}: hubs are looked up by name (a game lives in a mirror hub) and membership comes from the
 * core's {@link HubService}, which does not tell which hub a member is in: the client names the hub in its messages.
 */
@Component
class CoreHubGate implements HubGate {

	private final HubRepository hubRepository;
	private final HubService hubService;

	CoreHubGate(HubRepository hubRepository, HubService hubService) {
		this.hubRepository = hubRepository;
		this.hubService = hubService;
	}

	@Override
	public Optional<String> resolve(String hubName) {
		if (hubName == null || hubName.isBlank()) {
			return Optional.empty();
		}
		return hubRepository.findByNameIgnoreCase(hubName).filter(MirrorHubs::isMirror).map(Hub::getName);
	}

	@Override
	public boolean isMember(UUID account) {
		return hubService.isMember(account);
	}

	@Override
	public Set<UUID> membersWith(UUID account) {
		return hubService.sameHubMembers(account);
	}
}
