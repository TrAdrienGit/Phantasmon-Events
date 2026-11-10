package com.mystaria.phantasmon_backend.events;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mystaria.phantasmon_backend.addon.HubKind;
import com.mystaria.phantasmon_backend.admin.AdminService;
import com.mystaria.phantasmon_backend.common.ApiException;
import com.mystaria.phantasmon_backend.hub.Hub;
import com.mystaria.phantasmon_backend.hub.HubAnchor;
import com.mystaria.phantasmon_backend.hub.HubAnchorCreateRequest;
import com.mystaria.phantasmon_backend.hub.HubAnchorRepository;
import com.mystaria.phantasmon_backend.hub.HubCatalogController;
import com.mystaria.phantasmon_backend.hub.HubCatalogService;

import lombok.extern.slf4j.Slf4j;

/**
 * Mirror hubs (D-38) as a hub kind of the core (D-41): the whole height of the world, a square of side
 * {@code 2 * radius + 1}; their terrain is copied from their SOURCE anchor's zone, never a build. Admins' anchors only:
 * one SOURCE per hub, one COPY per admin, centred on the block posed from, never turned.
 */
@Service
@Slf4j
public class MirrorHubKind implements HubKind {

	private final HubAnchorRepository anchorRepository;
	private final AdminService adminService;
	private final HubCatalogService catalog;
	private final MirrorService mirrorService;

	public MirrorHubKind(HubAnchorRepository anchorRepository, AdminService adminService, HubCatalogService catalog,
			MirrorService mirrorService) {
		this.anchorRepository = anchorRepository;
		this.adminService = adminService;
		this.catalog = catalog;
		this.mirrorService = mirrorService;
	}

	@Override
	public String kind() {
		return MirrorHubs.KIND;
	}

	@Override
	public boolean requested(Map<String, Object> body) {
		return Boolean.TRUE.equals(body.get("mirror"));
	}

	@Override
	public int[] size(Map<String, Object> body) {
		Integer radius = HubCatalogController.integer(body, "radius");
		requireRadius(radius);
		return new int[] { 2 * radius + 1, MirrorHubs.HEIGHT, 2 * radius + 1 };
	}

	@Override
	public String anchorRole(UUID ownerUuid, Hub hub, String requested) {
		if (!adminService.isAdmin(ownerUuid)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ERROR_HUB_MIRROR_ADMIN_ONLY", Map.of("hub", hub.getName()));
		}
		if (MirrorHubs.ROLE_SOURCE.equals(requested)) {
			anchorRepository.findByHubUuidAndRole(hub.getUuid(), MirrorHubs.ROLE_SOURCE).ifPresent(existing -> {
				throw new ApiException(HttpStatus.CONFLICT, "ERROR_HUB_SOURCE_TAKEN",
						Map.of("anchor_uuid", existing.getUuid(), "name", existing.getName(), "hub", hub.getName()));
			});
			return requested;
		}
		if (MirrorHubs.ROLE_COPY.equals(requested)) {
			anchorRepository.findByOwnerUuidAndHubUuidAndRole(ownerUuid, hub.getUuid(), MirrorHubs.ROLE_COPY).stream().findFirst()
					.ifPresent(existing -> {
						throw new ApiException(HttpStatus.CONFLICT, "ERROR_HUB_ANCHOR_QUOTA",
								Map.of("anchor_uuid", existing.getUuid(), "name", existing.getName(), "hub", hub.getName()));
					});
			return requested;
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "ERROR_HUB_ANCHOR_ROLE", Map.of("hub", hub.getName()));
	}

	@Override
	public HubAnchor anchor(UUID ownerUuid, Hub hub, HubAnchorCreateRequest request, String role) {
		// Centred on the block posed from, the whole height of the world, never turned.
		return new HubAnchor(UUID.randomUUID(), ownerUuid, hub.getUuid(), request.name(), request.serverFingerprint(),
				request.dimension(), Math.floor(request.origin().x()) + 0.5, MirrorHubs.MIN_Y,
				Math.floor(request.origin().z()) + 0.5, (short) 0, role);
	}

	@Override
	public Map<String, Object> view(Hub hub) {
		return Map.of("mirror", true, "radius", MirrorHubs.radius(hub));
	}

	/** A mirror hub grows (or shrinks) by {@code delta} blocks of radius, around the same centre; its terrain follows. */
	@Transactional
	public HubCatalogService.HubView resize(UUID adminUuid, String name, Integer delta) {
		Hub hub = catalog.require(name);
		if (!MirrorHubs.isMirror(hub)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ERROR_HUB_NOT_MIRROR", Map.of("hub", hub.getName()));
		}
		int oldRadius = MirrorHubs.radius(hub);
		int newRadius = oldRadius + (delta == null ? 0 : delta);
		requireRadius(newRadius);
		HubCatalogService.HubView view = catalog.resize(hub, 2 * newRadius + 1, MirrorHubs.HEIGHT, 2 * newRadius + 1);
		mirrorService.onMirrorResized(hub, oldRadius);
		log.info("Admin {} resized mirror hub {}: radius {} -> {}", adminUuid, hub.getName(), oldRadius, newRadius);
		return view;
	}

	private static void requireRadius(Integer radius) {
		// No upper limit (Adrien 2026-10-29): admins only, their responsibility; the side fits a SMALLINT.
		if (radius == null || radius < 1 || radius > Short.MAX_VALUE / 2 - 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ERROR_HUB_INVALID_RADIUS", Map.of("min", 1));
		}
	}
}
