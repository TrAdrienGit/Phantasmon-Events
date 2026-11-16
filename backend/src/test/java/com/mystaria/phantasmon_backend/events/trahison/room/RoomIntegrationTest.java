package com.mystaria.phantasmon_backend.events.trahison.room;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The room bank's routes: admins only (definitions are spoilers), validated, blocks kept in a file. */
@Import(TestcontainersConfiguration.class)
// Same properties as LayoutIntegrationTest: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class RoomIntegrationTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private JwtService jwtService;
	@Autowired
	private PlayerService playerService;
	@Autowired
	private ObjectMapper mapper;

	private String adminToken;
	private String playerToken;
	private String id;

	@BeforeEach
	void setUp() throws Exception {
		id = "room_" + UUID.randomUUID().toString().substring(0, 8);
		String admin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		adminToken = tokenFor(UUID.randomUUID(), admin);
		playerToken = tokenFor(UUID.randomUUID(), "Pl" + UUID.randomUUID().toString().substring(0, 8));
		Files.createDirectories(ADMIN_FILE.getParent());
		String existing = Files.exists(ADMIN_FILE) ? Files.readString(ADMIN_FILE) : "";
		Files.writeString(ADMIN_FILE, existing + admin + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));
	}

	private String tokenFor(UUID uuid, String name) {
		playerService.recordConnection(uuid, name);
		return "Bearer " + jwtService.issueAccessToken(uuid, name);
	}

	private ResultActions save(String token, boolean put, RoomDefinition room) throws Exception {
		var request = put ? put("/events/trahison/rooms/" + id) : post("/events/trahison/rooms/" + id);
		return mockMvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(room)));
	}

	@Test
	void adminsSaveAndReadWithServerComputedFieldsAndPlayersSeeNothing() throws Exception {
		save(playerToken, true, RoomFixtures.chain()).andExpect(status().isForbidden());
		save(adminToken, true, RoomFixtures.chain()).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.difficulty").value(3))
				.andExpect(jsonPath("$.expected_seconds").value(80));
		save(adminToken, false, RoomFixtures.chain()).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
		mockMvc.perform(get("/events/trahison/rooms/" + id).header("Authorization", adminToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.steps.length()").value(3))
				.andExpect(jsonPath("$.steps[1].needs[0]").value("s1"));
		mockMvc.perform(get("/events/trahison/rooms").header("Authorization", adminToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$[*].id").value(hasItem(id)));
		mockMvc.perform(get("/events/trahison/rooms/nope_" + id).header("Authorization", adminToken))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value(RoomCatalog.NOT_FOUND));
		// A definition holds the hints and the chain: a spoiler, so a player may read none of it.
		mockMvc.perform(get("/events/trahison/rooms/" + id).header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(get("/events/trahison/rooms").header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(get("/events/trahison/rooms/" + id + "/schematic").header("Authorization", playerToken))
				.andExpect(status().isForbidden());
	}

	@Test
	void anInvalidRoomIsRefusedWithEveryError() throws Exception {
		var bad = new RoomDefinition(id, "Bad", null, 0, 0, 0, List.of(),
				List.of(RoomFixtures.step("a", "debug_button", java.util.Map.of(), List.of("zzz"), List.of())), null);
		save(adminToken, true, bad).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value(RoomCatalog.INVALID))
				.andExpect(jsonPath("$.details.errors[*].code").value(hasItem(RoomValidator.UNKNOWN_STEP)))
				.andExpect(jsonPath("$.details.errors[*].code").value(hasItem(RoomValidator.NO_EXIT)));
	}

	@Test
	void schematicIsUploadedAndDownloadedByAdmins() throws Exception {
		byte[] blocks = { 1, 2, 3, 4, 5, (byte) 200 };
		mockMvc.perform(post("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_OCTET_STREAM).content(blocks)).andExpect(status().isNotFound());
		save(adminToken, true, RoomFixtures.chain()).andExpect(status().isOk());
		mockMvc.perform(get("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error_code").value(RoomCatalog.SCHEMATIC_MISSING));
		mockMvc.perform(post("/events/trahison/rooms/" + id + "/schematic").header("Authorization", playerToken)
				.contentType(MediaType.APPLICATION_OCTET_STREAM).content(blocks)).andExpect(status().isForbidden());
		mockMvc.perform(post("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_OCTET_STREAM).content(blocks)).andExpect(status().isOk());
		byte[] back = mockMvc.perform(get("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(blocks, back);
		mockMvc.perform(get("/events/trahison/rooms").header("Authorization", adminToken))
				.andExpect(jsonPath("$[?(@.id == '" + id + "')].has_schematic").value(hasItem(true)));
	}

	@Test
	void schematicJsonRoundTripsBase64AndSharesTheBinaryRoute() throws Exception {
		byte[] blocks = new byte[5000];
		new java.util.Random(7).nextBytes(blocks);
		String body = "{\"data\":\"" + java.util.Base64.getEncoder().encodeToString(blocks) + "\"}";
		String url = "/events/trahison/rooms/" + id + "/schematic.json";
		mockMvc.perform(post(url).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isNotFound());
		save(adminToken, true, RoomFixtures.chain()).andExpect(status().isOk());
		mockMvc.perform(get(url).header("Authorization", adminToken)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error_code").value(RoomCatalog.SCHEMATIC_MISSING));
		mockMvc.perform(post(url).header("Authorization", playerToken).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(url).header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(post(url).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk()).andExpect(jsonPath("$.size").value(blocks.length));
		String json = mockMvc.perform(get(url).header("Authorization", adminToken)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.version").value(1))
				.andReturn().getResponse().getContentAsString();
		assertArrayEquals(blocks, java.util.Base64.getDecoder().decode(mapper.readTree(json).get("data").asString()));
		// Same file as the binary route.
		byte[] binary = mockMvc.perform(get("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken))
				.andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(blocks, binary);
		// Bad base64, empty and missing data are refused with the schematic error.
		for (String bad : List.of("{\"data\":\"@@@\"}", "{\"data\":\"\"}", "{}")) {
			mockMvc.perform(post(url).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(bad))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value(RoomCatalog.SCHEMATIC_INVALID));
		}
	}

	@Test
	void schematicJsonAcceptsTheLimitAndRefusesAboveIt() throws Exception {
		save(adminToken, true, RoomFixtures.chain()).andExpect(status().isOk());
		String url = "/events/trahison/rooms/" + id + "/schematic.json";
		byte[] max = new byte[RoomCatalog.MAX_SCHEMATIC_BYTES];
		java.util.Arrays.fill(max, (byte) 7);
		mockMvc.perform(post(url).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"data\":\"" + java.util.Base64.getEncoder().encodeToString(max) + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.size").value(max.length));
		byte[] over = new byte[RoomCatalog.MAX_SCHEMATIC_BYTES + 1];
		mockMvc.perform(post(url).header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"data\":\"" + java.util.Base64.getEncoder().encodeToString(over) + "\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value(RoomCatalog.SCHEMATIC_INVALID));
	}

	@Test
	void mechanismsAreListedForAdminsOnly() throws Exception {
		mockMvc.perform(get("/events/trahison/mechanisms").header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(get("/events/trahison/mechanisms").header("Authorization", adminToken)).andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == 'debug_button')].difficulty").value(hasItem(1)))
				.andExpect(jsonPath("$[?(@.id == 'debug_button')].expected_seconds").value(hasItem(10)))
				.andExpect(jsonPath("$[?(@.id == 'three_dials')].clue_roles[0]").value(hasItem("code")))
				.andExpect(jsonPath("$[?(@.id == 'three_dials')].expected_seconds").value(hasItem(60)))
				.andExpect(jsonPath("$[?(@.id == 'three_dials')].slot_roles[0].role").value(hasItem("dials")))
				.andExpect(jsonPath("$[?(@.id == 'keypad')].slot_roles[0].role").value(hasItem("keypad")))
				.andExpect(jsonPath("$[?(@.id == 'keypad')].slot_roles[0].kinds[0]").value(hasItem("keypad")))
				.andExpect(jsonPath("$[?(@.id == 'keypad')].clue_roles[0]").value(hasItem("code")))
				.andExpect(jsonPath("$[?(@.id == 'pickup_set')].slot_roles[0].role").value(hasItem("deposit")))
				.andExpect(jsonPath("$[?(@.id == 'pickup_set')].clue_roles[0]").value(hasItem("spot_1")))
				.andExpect(jsonPath("$[?(@.id == 'ring_dial')].slot_roles[0].role").value(hasItem("terminal")))
				.andExpect(jsonPath("$[?(@.id == 'wiring')].slot_roles[0].role").value(hasItem("panel")))
				.andExpect(jsonPath("$[?(@.id == 'wiring')].clue_roles[0]").value(hasItem("scheme")))
				.andExpect(jsonPath("$[?(@.id == 'code_breaker')].slot_roles[0].role").value(hasItem("terminal")))
				.andExpect(jsonPath("$[?(@.id == 'code_breaker')].params[0].name").value(hasItem("length")))
				.andExpect(jsonPath("$[0].params").isArray());
	}

	@Test
	void deleteIsForAdminsAndRemovesBlocksToo() throws Exception {
		save(adminToken, true, RoomFixtures.chain()).andExpect(status().isOk());
		mockMvc.perform(post("/events/trahison/rooms/" + id + "/schematic").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[] { 9 })).andExpect(status().isOk());
		mockMvc.perform(delete("/events/trahison/rooms/" + id).header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/events/trahison/rooms/" + id).header("Authorization", adminToken)).andExpect(status().isOk());
		mockMvc.perform(get("/events/trahison/rooms/" + id).header("Authorization", adminToken)).andExpect(status().isNotFound());
		mockMvc.perform(delete("/events/trahison/rooms/" + id).header("Authorization", adminToken)).andExpect(status().isNotFound());
	}

	@Test
	void aBadIdIsRefused() throws Exception {
		mockMvc.perform(get("/events/trahison/rooms/bad.id").header("Authorization", adminToken))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error_code").value(RoomCatalog.BAD_ID));
	}
}
