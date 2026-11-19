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
				// The public site Village 14 (SitePages): home, tournaments (read only, no player UUIDs), downloads; old /t, /d.
				new PublicRoute(HttpMethod.GET, "/", "/home", "/home/", "/site/*", "/league", "/league/", "/league/data", "/league/fields", "/tournament", "/tournament/", "/tournament/*", "/download", "/download/", "/download/latest/*",
						"/download/files", "/t", "/t/", "/t/*", "/d", "/d/", "/d/latest/*", "/public/tournaments", "/public/tournaments/*", "/public/heads/*"),
				// Our dedicated server's mirror copy: checked by its own token (ServerMirrorController).
				new PublicRoute(HttpMethod.GET, "/server/mirror/**"),
				new PublicRoute(HttpMethod.POST, "/server/mirror/*/ghosts"));
	}
}
