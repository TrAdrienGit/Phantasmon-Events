package com.mystaria.phantasmon_backend.events.npc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;
import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.addon.NpcOpponentProvider.NpcOpponent;
import com.mystaria.phantasmon_backend.auth.JwtService;
import com.mystaria.phantasmon_backend.player.PlayerService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** An event NPC's imposed Showtime intro and ambiance (2026-11-18): saved, shown, handed to the core's battles, cleared. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class EventNpcShowtimeIntegrationTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PlayerService playerService;

	@Autowired
	private EventNpcService service;

	@Test
	void introAndAmbianceAreSavedAndImposed() throws Exception {
		String admin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		UUID adminUuid = UUID.randomUUID();
		playerService.recordConnection(adminUuid, admin);
		String token = "Bearer " + jwtService.issueAccessToken(adminUuid, admin);
		Files.createDirectories(ADMIN_FILE.getParent());
		Files.writeString(ADMIN_FILE, admin + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));

		String created = mockMvc.perform(post("/admin/events/npcs").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"event\":\"ligue\",\"name\":\"Pierre\",\"server_fingerprint\":\"test\",\"dimension\":\"minecraft:overworld\","
						+ "\"x\":0,\"y\":64,\"z\":0,\"yaw\":0}"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		UUID npc = UUID.fromString(JsonMapper.builder().build().readTree(created).get("uuid").asString());

		mockMvc.perform(post("/admin/events/npcs/" + npc).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"intro\":\"phantasmon_events:gym_rock\",\"ambiance\":\"phantasmon_events:gym_leader\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.intro").value("phantasmon_events:gym_rock"))
				.andExpect(jsonPath("$.ambiance").value("phantasmon_events:gym_leader"));
		NpcOpponent opponent = service.npcOpponent(adminUuid, npc);
		assertThat(opponent.intro()).isEqualTo("phantasmon_events:gym_rock");
		assertThat(opponent.ambiance()).isEqualTo("phantasmon_events:gym_leader");

		mockMvc.perform(post("/admin/events/npcs/" + npc).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"intro\":\"PBR_ENTRANCE\",\"ambiance\":\"\"}"))
				.andExpect(jsonPath("$.intro").value("PBR_ENTRANCE"))
				.andExpect(jsonPath("$.ambiance").value(nullValue()));
		mockMvc.perform(post("/admin/events/npcs/" + npc).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"intro\":\"not an intro!\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_NPC_INTRO"));
	}
}
