package com.mystaria.phantasmon_backend.events.trahison.room;

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
import org.springframework.test.web.servlet.MockMvc;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.auth.JwtService;
import com.mystaria.phantasmon_backend.events.trahison.telemetry.StepTimes;
import com.mystaria.phantasmon_backend.events.trahison.room.mechanism.Json;
import com.mystaria.phantasmon_backend.player.PlayerService;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T3.6: the step times table, the medians route (admins only) and the measured time shown in the room list. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class StepTimesTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	PlayerService playerService;
	@Autowired
	StepTimes times;
	@Autowired
	RoomCatalog catalog;

	String adminToken;
	String playerToken;
	String room;

	@BeforeEach
	void setUp() throws Exception {
		room = "timed_" + UUID.randomUUID().toString().substring(0, 8);
		String admin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		UUID adminId = UUID.randomUUID();
		playerService.recordConnection(adminId, admin);
		adminToken = "Bearer " + jwtService.issueAccessToken(adminId, admin);
		UUID playerId = UUID.randomUUID();
		String player = "Pl" + UUID.randomUUID().toString().substring(0, 8);
		playerService.recordConnection(playerId, player);
		playerToken = "Bearer " + jwtService.issueAccessToken(playerId, player);
		Files.createDirectories(ADMIN_FILE.getParent());
		String existing = Files.exists(ADMIN_FILE) ? Files.readString(ADMIN_FILE) : "";
		Files.writeString(ADMIN_FILE, existing + admin + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));
	}

	private void measure(String roomId, String step, int seconds) {
		times.record(roomId, 1, step, "debug_button", Json.object(), seconds, 0, 3);
	}

	@Test
	void mediansPerRoomAndPerMechanismAndTheRouteIsForAdmins() throws Exception {
		measure(room, "s1", 100);
		measure(room, "s1", 300);
		measure(room, "s1", 200);
		measure(room, "s2", 50);
		var byRoom = times.byRoomAndStep().get(room);
		assertEquals(200, byRoom.get("s1").seconds());
		assertEquals(3, byRoom.get("s1").count());
		assertEquals(1, byRoom.get("s2").count());
		assertTrue(times.byMechanism().stream().anyMatch(m -> "debug_button".equals(m.get("mechanism"))));

		mockMvc.perform(get("/events/trahison/telemetry/step-times").header("Authorization", playerToken)).andExpect(status().isForbidden());
		mockMvc.perform(get("/events/trahison/telemetry/step-times").header("Authorization", adminToken)).andExpect(status().isOk())
				.andExpect(jsonPath("$.mechanisms[*].mechanism").value(hasItem("debug_button")))
				.andExpect(jsonPath("$.rooms[?(@.room == '" + room + "')].median_seconds").value(hasItem(250)))
				.andExpect(jsonPath("$.rooms[?(@.room == '" + room + "')].steps[0].median_seconds").value(hasItem(200)));
	}

	@Test
	void theDisplayedTimeUsesTheMeasureFromThreeMeasures() {
		catalog.save(UUID.randomUUID(), room, RoomFixtures.chain());
		RoomSummary estimated = summary();
		assertFalse(estimated.measured());
		measure(room, "s1", 1000);
		measure(room, "s1", 1000);
		assertFalse(summary().measured(), "two measures are not enough");
		assertEquals(estimated.expectedSeconds(), summary().expectedSeconds());
		measure(room, "s1", 1000);
		RoomSummary measured = summary();
		assertTrue(measured.measured());
		// s1 (a debug button, estimated 10 s) now counts for 1000 s
		assertEquals(estimated.expectedSeconds() - 10 + 1000, measured.expectedSeconds());
	}

	private RoomSummary summary() {
		return catalog.list().stream().filter(r -> r.id().equals(room)).findFirst().orElseThrow();
	}
}
