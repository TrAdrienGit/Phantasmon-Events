package com.mystaria.phantasmon_backend.events;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import com.mystaria.phantasmon_backend.addon.PublicRoutesProvider;

/** Phantasmon Events' routes reachable without a player's token (TODO-35). */
@Component
public class EventsPublicRoutes implements PublicRoutesProvider {

	@Override
	public List<PublicRoute> publicRoutes() {
		return List.of(
				// Tournaments' public page (read only, no player UUIDs).
				new PublicRoute(HttpMethod.GET, "/t", "/t/", "/t/*", "/public/tournaments", "/public/tournaments/*", "/public/heads/*"),
				// Our dedicated server's mirror copy: checked by its own token (ServerMirrorController).
				new PublicRoute(HttpMethod.GET, "/server/mirror/**"),
				new PublicRoute(HttpMethod.POST, "/server/mirror/*/ghosts"));
	}
}
