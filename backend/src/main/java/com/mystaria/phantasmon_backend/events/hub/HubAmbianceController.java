package com.mystaria.phantasmon_backend.events.hub;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;
import com.mystaria.phantasmon_backend.common.ApiException;
import com.mystaria.phantasmon_backend.hub.Hub;
import com.mystaria.phantasmon_backend.hub.HubRepository;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;
import com.mystaria.phantasmon_backend.websocket.WsMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Hub ambiances (2026-11-19): one Showtime ambiance per hub, the same for everyone in it, battle or not.
 * <ul>
 * <li>{@code GET /events/hubs/ambiances}: {@code [{ "hub", "ambiance" }]}, every hub that has one;</li>
 * <li>{@code POST /admin/events/hubs/{name}/ambiance} {@code {"ambiance": "<id>"}} (admins; "" or null: none).</li>
 * </ul>
 * Every change is broadcast ({@code EventsHubAmbiancesChanged}): clients list them again.
 */
@RestController
@Slf4j
public class HubAmbianceController {

	/** A Showtime ambiance id: {@code namespace:path} or a bare path. */
	private static final Pattern AMBIANCE = Pattern.compile("[a-z0-9_.-]+(:[a-z0-9_./-]+)?");

	private final HubAmbianceRepository ambiances;
	private final HubRepository hubs;
	private final AdminService adminService;
	private final SessionRegistry sessionRegistry;
	private final Clock clock;

	public HubAmbianceController(HubAmbianceRepository ambiances, HubRepository hubs, AdminService adminService,
			SessionRegistry sessionRegistry, Clock clock) {
		this.ambiances = ambiances;
		this.hubs = hubs;
		this.adminService = adminService;
		this.sessionRegistry = sessionRegistry;
		this.clock = clock;
	}

	@GetMapping("/events/hubs/ambiances")
	@Transactional(readOnly = true)
	public List<Map<String, Object>> list() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (HubAmbiance row : ambiances.findAll()) {
			hubs.findById(row.getHubUuid()).ifPresent(hub -> {
				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("hub", hub.getName());
				entry.put("ambiance", row.getAmbiance());
				list.add(entry);
			});
		}
		return list;
	}

	@PostMapping("/admin/events/hubs/{name}/ambiance")
	@Transactional
	public Map<String, Object> set(@PathVariable String name, @RequestBody(required = false) Map<String, Object> body,
			Authentication authentication) {
		UUID admin = (UUID) authentication.getPrincipal();
		adminService.requireAdmin(admin);
		Hub hub = hubs.findByNameIgnoreCase(name)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ERROR_HUB_NOT_FOUND", Map.of("hub", name)));
		Object raw = body == null ? null : body.get("ambiance");
		String ambiance = raw == null ? "" : raw.toString().strip();
		if (ambiance.length() > 130 || !ambiance.isEmpty() && !AMBIANCE.matcher(ambiance).matches()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ERROR_HUB_AMBIANCE", Map.of("hub", hub.getName()));
		}
		// Rows of hubs deleted meanwhile go with the next change.
		ambiances.findAll().stream().filter(row -> !hubs.existsById(row.getHubUuid())).forEach(ambiances::delete);
		if (ambiance.isEmpty()) {
			ambiances.deleteById(hub.getUuid());
		} else {
			HubAmbiance row = ambiances.findById(hub.getUuid()).orElse(null);
			if (row == null) {
				ambiances.save(new HubAmbiance(hub.getUuid(), ambiance, admin, clock.instant()));
			} else {
				row.setAmbiance(ambiance);
				row.setUpdatedBy(admin);
				row.setUpdatedAt(clock.instant());
			}
		}
		log.info("Admin {} set hub {}'s ambiance to {}", admin, hub.getName(), ambiance.isEmpty() ? "none" : ambiance);
		sessionRegistry.broadcast(WsMessage.of("EventsHubAmbiancesChanged", Map.of()));
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("hub", hub.getName());
		result.put("ambiance", ambiance.isEmpty() ? null : ambiance);
		return result;
	}
}
