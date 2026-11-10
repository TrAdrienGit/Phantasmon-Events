package com.mystaria.phantasmon_backend.events;

import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.BackendAddon;

/** Phantasmon Events' backend module, announced by {@code GET /version} (D-41). */
@Component
public class EventsAddon implements BackendAddon {

	@Override
	public String id() {
		return "phantasmon_events";
	}

	@Override
	public String version() {
		String version = EventsAddon.class.getPackage().getImplementationVersion();
		return version == null ? "dev" : version;
	}
}
