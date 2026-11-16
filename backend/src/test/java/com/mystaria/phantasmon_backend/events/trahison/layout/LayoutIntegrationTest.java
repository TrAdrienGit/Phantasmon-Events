package com.mystaria.phantasmon_backend.events.trahison.layout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The plan's routes: read by anyone, saved by admins only, versioned, validated. */
@Import(TestcontainersConfiguration.class)
// Same properties as MirrorHubIntegrationTest: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class LayoutIntegrationTest {

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
	private String hub;

	@BeforeEach
	void setUp() throws Exception {
		hub = "lay_" + UUID.randomUUID().toString().substring(0, 8);
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

	private ResultActions save(String token, boolean put, LayoutContent content, boolean draft, Integer base) throws Exception {
		var body = new LayoutRequest(base, draft, content.zones(), content.doors(), content.roomSlots(), content.cabins(), content.hidingSpots());
		var request = put ? put("/events/trahison/layout/" + hub) : post("/events/trahison/layout/" + hub);
		return mockMvc.perform(request.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(body)));
	}

	@Test
	void aHubWithoutPlanGivesVersionZero() throws Exception {
		mockMvc.perform(get("/events/trahison/layout/" + hub).header("Authorization", playerToken))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(0))
				.andExpect(jsonPath("$.zones.length()").value(0));
	}

	@Test
	void onlyAdminsSave() throws Exception {
		save(playerToken, true, LayoutFixtures.complete(), false, null).andExpect(status().isForbidden());
		mockMvc.perform(get("/events/trahison/layout/" + hub).header("Authorization", playerToken))
				.andExpect(jsonPath("$.version").value(0));
	}

	@Test
	void aCompletePlanIsSavedVersionedAndReadBackIdentically() throws Exception {
		save(adminToken, true, LayoutFixtures.complete(), false, 0)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.draft").value(false));
		// Same plan through POST, on top of version 1.
		save(adminToken, false, LayoutFixtures.complete(), false, 1)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(2));
		LayoutContent expected = LayoutFixtures.complete();
		String json = mockMvc.perform(get("/events/trahison/layout/" + hub).header("Authorization", playerToken))
				.andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
				.andExpect(jsonPath("$.zones[0].boxes[0].min_x").value(expected.zones().get(0).boxes().get(0).minX()))
				.andReturn().getResponse().getContentAsString();
		Layout read = mapper.readValue(json, Layout.class);
		org.assertj.core.api.Assertions.assertThat(read.content()).isEqualTo(expected);
	}

	@Test
	void anIncompletePlanIsRefusedWithEveryErrorUnlessItIsADraft() throws Exception {
		save(adminToken, true, LayoutContent.empty(), false, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_TRAHISON_LAYOUT_INVALID"))
				.andExpect(jsonPath("$.details.errors[*].code").value(hasItem(LayoutValidator.FINAL_DOOR)))
				.andExpect(jsonPath("$.details.errors[*].code").value(hasItem(LayoutValidator.CABINS)));
		save(adminToken, true, LayoutContent.empty(), true, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.version").value(1))
				.andExpect(jsonPath("$.draft").value(true));
	}

	@Test
	void aSaveOnTopOfANewerVersionIsAConflict() throws Exception {
		save(adminToken, true, LayoutContent.empty(), true, 0).andExpect(status().isOk());
		save(adminToken, true, LayoutContent.empty(), true, 0)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error_code").value("ERROR_TRAHISON_LAYOUT_CONFLICT"))
				.andExpect(jsonPath("$.details.version").value(1));
	}
}
