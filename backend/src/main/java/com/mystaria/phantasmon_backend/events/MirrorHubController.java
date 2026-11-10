package com.mystaria.phantasmon_backend.events;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;
import com.mystaria.phantasmon_backend.hub.HubCatalogService;

/** Admins resize a mirror hub (D-38); moved from the core's hub catalog (D-41). */
@RestController
public class MirrorHubController {

	private final MirrorHubKind mirrorHubs;
	private final AdminService adminService;

	public MirrorHubController(MirrorHubKind mirrorHubs, AdminService adminService) {
		this.mirrorHubs = mirrorHubs;
		this.adminService = adminService;
	}

	public record HubResizeRequest(Integer delta) {
	}

	/** A mirror hub's radius grows by {@code delta} (negative: shrinks). */
	@PostMapping("/admin/hubs/{name}/resize")
	public HubCatalogService.HubView resize(@PathVariable String name, @RequestBody(required = false) HubResizeRequest request,
			Authentication authentication) {
		UUID admin = (UUID) authentication.getPrincipal();
		adminService.requireAdmin(admin);
		return mirrorHubs.resize(admin, name, request == null ? null : request.delta());
	}
}
