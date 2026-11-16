package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;

/**
 * The betrayal game's fixed plan (Documentation/trahison/protocole.md section 4):
 * <ul>
 * <li>{@code GET /events/trahison/layout/{hub}}: the latest version, for any signed-in player (zones, doors and cabins
 * are not secrets: clients need them to know where they are);</li>
 * <li>{@code PUT /events/trahison/layout/{hub}}: save a new version, admins only. {@code POST} on the same path does
 * the same, for the client's HTTP helper that has no PUT.</li>
 * </ul>
 */
@RestController
public class LayoutController {

	private final LayoutService layouts;
	private final AdminService adminService;

	public LayoutController(LayoutService layouts, AdminService adminService) {
		this.layouts = layouts;
		this.adminService = adminService;
	}

	/** The latest plan; with {@code ?version=n} that version (a running game keeps the one it started with). */
	@GetMapping("/events/trahison/layout/{hub}")
	public Layout get(@PathVariable String hub, @RequestParam(required = false) Integer version) {
		return version == null ? layouts.latest(hub) : layouts.version(hub, version);
	}

	@PutMapping("/events/trahison/layout/{hub}")
	public Layout put(@PathVariable String hub, @RequestBody LayoutRequest request, Authentication authentication) {
		return save(hub, request, authentication);
	}

	@PostMapping("/events/trahison/layout/{hub}")
	public Layout post(@PathVariable String hub, @RequestBody LayoutRequest request, Authentication authentication) {
		return save(hub, request, authentication);
	}

	private Layout save(String hub, LayoutRequest request, Authentication authentication) {
		UUID admin = (UUID) authentication.getPrincipal();
		adminService.requireAdmin(admin);
		return layouts.save(admin, hub, request);
	}
}
