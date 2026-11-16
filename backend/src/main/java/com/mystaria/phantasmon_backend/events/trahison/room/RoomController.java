package com.mystaria.phantasmon_backend.events.trahison.room;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mystaria.phantasmon_backend.admin.AdminService;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.MechanismRegistry;

import tools.jackson.databind.JsonNode;

/**
 * The room bank's routes (Documentation/trahison/protocole.md section 4.3): admins only, reading as well as writing (a
 * definition holds the hint texts and the chain: a spoiler for the players). {@code POST} does what {@code PUT} does, for the client's HTTP helper that has no PUT.
 */
@RestController
public class RoomController {

	private final RoomCatalog rooms;
	private final AdminService adminService;
	private final MechanismRegistry mechanisms;

	public RoomController(RoomCatalog rooms, AdminService adminService, MechanismRegistry mechanisms) {
		this.rooms = rooms;
		this.adminService = adminService;
		this.mechanisms = mechanisms;
	}

	/** Every mechanism the backend has, for the room editor (admins: the clue roles hint at the solutions' shape). */
	@GetMapping("/events/trahison/mechanisms")
	public List<MechanismInfo> mechanisms(Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		JsonNode defaults = Json.object();
		return mechanisms.all().stream().map(m -> new MechanismInfo(m.id(), m.clueRoles(defaults), m.params(),
				m.difficulty(defaults), m.expectedSeconds(defaults))).toList();
	}

	@GetMapping("/events/trahison/rooms")
	public List<RoomSummary> list(Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		return rooms.list();
	}

	@GetMapping("/events/trahison/rooms/{id}")
	public RoomDefinition get(@PathVariable String id, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		return rooms.get(id);
	}

	@PutMapping("/events/trahison/rooms/{id}")
	public RoomDefinition put(@PathVariable String id, @RequestBody RoomDefinition body, Authentication authentication) {
		return save(id, body, authentication);
	}

	@PostMapping("/events/trahison/rooms/{id}")
	public RoomDefinition post(@PathVariable String id, @RequestBody RoomDefinition body, Authentication authentication) {
		return save(id, body, authentication);
	}

	@DeleteMapping("/events/trahison/rooms/{id}")
	public Map<String, Object> delete(@PathVariable String id, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		rooms.delete(id);
		return Map.of("deleted", id);
	}

	@GetMapping(value = "/events/trahison/rooms/{id}/schematic", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<byte[]> schematic(@PathVariable String id, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(rooms.readSchematic(id));
	}

	@PostMapping(value = "/events/trahison/rooms/{id}/schematic", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Map<String, Object> uploadPost(@PathVariable String id, @RequestBody byte[] bytes, Authentication authentication) {
		return upload(id, bytes, authentication);
	}

	@PutMapping(value = "/events/trahison/rooms/{id}/schematic", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public Map<String, Object> uploadPut(@PathVariable String id, @RequestBody byte[] bytes, Authentication authentication) {
		return upload(id, bytes, authentication);
	}

	/** The same blocks as {@code GET .../schematic}, as JSON for clients that only speak JSON: {@code data} is base64. */
	@GetMapping("/events/trahison/rooms/{id}/schematic.json")
	public Map<String, Object> schematicJson(@PathVariable String id, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		byte[] bytes = rooms.readSchematic(id);
		return Map.of("id", id, "version", rooms.get(id).version(), "data", Base64.getEncoder().encodeToString(bytes));
	}

	/** The same storage and validation as the binary upload; body {@code {"data": base64}}. */
	@PostMapping("/events/trahison/rooms/{id}/schematic.json")
	public Map<String, Object> uploadJson(@PathVariable String id, @RequestBody SchematicBody body, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		return upload(id, rooms.decodeSchematic(body == null ? null : body.data()), authentication);
	}

	/** Body of the JSON upload. */
	public record SchematicBody(String data) {
	}

	private RoomDefinition save(String id, RoomDefinition body, Authentication authentication) {
		UUID admin = (UUID) authentication.getPrincipal();
		adminService.requireAdmin(admin);
		return rooms.save(admin, id, body);
	}

	private Map<String, Object> upload(String id, byte[] bytes, Authentication authentication) {
		adminService.requireAdmin((UUID) authentication.getPrincipal());
		rooms.writeSchematic(id, bytes);
		return Map.of("id", id, "size", bytes.length);
	}
}
