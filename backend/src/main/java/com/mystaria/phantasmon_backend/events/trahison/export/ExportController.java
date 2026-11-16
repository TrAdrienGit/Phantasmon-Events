package com.mystaria.phantasmon_backend.events.trahison.export;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;

/**
 * The paste files of a game (D80), admins only (protocole.md, "Fichiers de collage"). JSON in both directions because the
 * client's HTTP helper only speaks JSON: {@code data} is base64.
 */
@RestController
public class ExportController {

	private final ExportStore store;
	private final AdminService adminService;

	public ExportController(ExportStore store, AdminService adminService) {
		this.store = store;
		this.adminService = adminService;
	}

	public record FileBody(String data) {
	}

	@GetMapping("/events/trahison/exports")
	public List<ExportStore.Game> games(Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		return store.games();
	}

	@PostMapping("/events/trahison/exports/{game}/files/{name}")
	public Map<String, Object> upload(@PathVariable String game, @PathVariable String name, @RequestBody FileBody body,
			Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		byte[] bytes = store.decode(body == null ? null : body.data());
		store.write(game, name, bytes);
		return Map.of("game", game, "name", name, "size", bytes.length);
	}

	@GetMapping("/events/trahison/exports/{game}/files/{name}")
	public Map<String, Object> download(@PathVariable String game, @PathVariable String name, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		byte[] bytes = store.read(game, name);
		return Map.of("game", game, "name", name, "size", bytes.length, "data", Base64.getEncoder().encodeToString(bytes));
	}
}
