package com.mystaria.phantasmon_backend.events;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.auth.JwtService;
import com.mystaria.phantasmon_backend.player.PlayerService;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mirror hubs (D-38) as Phantasmon Events' hub kind (D-41): created, shown, anchored and resized through the core's
 * generic hub kinds, with the same API as before the core stopped knowing them.
 */
@Import(TestcontainersConfiguration.class)
// Same properties as AdminControllerTest and HubAnchorControllerTest: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class MirrorHubIntegrationTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PlayerService playerService;

	private String server;
	private String adminToken;
	private String otherAdminToken;
	private String playerToken;
	private String hub;

	@BeforeEach
	void setUp() throws Exception {
		server = "srv-" + UUID.randomUUID();
		hub = "t_" + UUID.randomUUID().toString().substring(0, 8);
		String admin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		String otherAdmin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		adminToken = tokenFor(UUID.randomUUID(), admin);
		otherAdminToken = tokenFor(UUID.randomUUID(), otherAdmin);
		playerToken = tokenFor(UUID.randomUUID(), "Pl" + UUID.randomUUID().toString().substring(0, 8));
		Files.createDirectories(ADMIN_FILE.getParent());
		Files.writeString(ADMIN_FILE, admin + "\n" + otherAdmin + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));
	}

	@AfterEach
	void removeHub() throws Exception {
		mockMvc.perform(delete("/admin/hubs/" + hub).header("Authorization", adminToken));
		// An ordinary hub's schematic folder, archived by the deletion.
		Path schematics = Path.of("build/test-hub_schematics");
		if (!Files.isDirectory(schematics)) {
			return;
		}
		try (var folders = Files.list(schematics)) {
			for (Path folder : folders.filter(path -> path.getFileName().toString().startsWith("hub_" + hub)).toList()) {
				try (var files = Files.walk(folder)) {
					for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
		}
	}

	private String tokenFor(UUID uuid, String name) {
		playerService.recordConnection(uuid, name);
		return "Bearer " + jwtService.issueAccessToken(uuid, name);
	}

	private ResultActions anchor(String token, String name, String role, double x) throws Exception {
		return mockMvc.perform(post("/hub/anchors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"request_uuid":"%s","hub":"%s","name":"%s","server_fingerprint":"%s","dimension":"minecraft:overworld",
						 "origin":{"x":%s,"y":70.0,"z":10.2},"yaw":37.0%s}
						""".formatted(UUID.randomUUID(), hub, name, server, x, role == null ? "" : ",\"role\":\"" + role + "\"")));
	}

	@Test
	void aMirrorHubIsCreatedShownAnchoredAndResizedAsBefore() throws Exception {
		mockMvc.perform(post("/admin/hubs").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"%s\",\"mirror\":true,\"radius\":0}".formatted(hub)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_INVALID_RADIUS"));
		mockMvc.perform(post("/admin/hubs").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"%s\",\"mirror\":true,\"radius\":5}".formatted(hub)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("MIRROR"))
				.andExpect(jsonPath("$.mirror").value(true))
				.andExpect(jsonPath("$.radius").value(5))
				.andExpect(jsonPath("$.size.x").value(11))
				.andExpect(jsonPath("$.size.y").value(384))
				.andExpect(jsonPath("$.size.z").value(11));
		mockMvc.perform(get("/hubs").header("Authorization", playerToken))
				.andExpect(jsonPath("$[?(@.name == '%s')].mirror".formatted(hub)).value(true))
				.andExpect(jsonPath("$[?(@.name == 'global')].kind").value("NORMAL"))
				.andExpect(jsonPath("$[?(@.name == 'global')].mirror").isEmpty());

		// Admins only, SOURCE or COPY; one SOURCE per hub, one COPY per admin.
		anchor(playerToken, "pl-copy", "COPY", 0).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_MIRROR_ADMIN_ONLY"));
		anchor(adminToken, "no-role", null, 0).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_ANCHOR_ROLE"));
		anchor(adminToken, "source", "SOURCE", 3.7).andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("SOURCE"))
				.andExpect(jsonPath("$.mirror").value(true))
				.andExpect(jsonPath("$.kind").value("MIRROR"))
				.andExpect(jsonPath("$.origin.x").value(3.5))
				.andExpect(jsonPath("$.origin.y").value(-64.0))
				.andExpect(jsonPath("$.origin.z").value(10.5))
				.andExpect(jsonPath("$.yaw").value(0));
		anchor(otherAdminToken, "source2", "SOURCE", 500).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_SOURCE_TAKEN"));
		anchor(adminToken, "copy", "COPY", 3.7).andExpect(status().isCreated());
		anchor(adminToken, "copy2", "COPY", 900).andExpect(status().isConflict())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_ANCHOR_QUOTA"));
		anchor(otherAdminToken, "copy3", "COPY", 900).andExpect(status().isCreated());

		mockMvc.perform(post("/admin/hubs/" + hub + "/resize").header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.radius").value(8))
				.andExpect(jsonPath("$.size.x").value(17));
		mockMvc.perform(post("/admin/hubs/global/resize").header("Authorization", adminToken)
						.contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_NOT_MIRROR"));
	}

	@Test
	void theBackendAnnouncesTheEventsModule() throws Exception {
		mockMvc.perform(get("/version"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.addons.phantasmon_events").isNotEmpty());
	}

	@Test
	void anOrdinaryHubShowsNoMirrorFields() throws Exception {
		mockMvc.perform(post("/admin/hubs").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"%s\",\"length\":9,\"width\":9,\"height\":9}".formatted(hub)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("NORMAL"))
				.andExpect(jsonPath("$.mirror").doesNotExist())
				.andExpect(jsonPath("$.schematic").value(nullValue()));
		anchor(adminToken, "normal", "COPY", 0).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_HUB_ANCHOR_ROLE"));
	}
}
