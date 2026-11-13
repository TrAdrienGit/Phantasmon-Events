package com.mystaria.phantasmon_backend.events.tournament;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * A tournament's dates (plan-tournois.md §8, 2026-11-11): kept in order, cleared on demand, and the steps they
 * trigger — the check-in opening at its date, the tournament starting at the check-in's closing with those present,
 * or staying in the check-in when too few are there.
 */
@Import(TestcontainersConfiguration.class)
// Same properties as the other integration tests: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
@AutoConfigureMockMvc
class TournamentDatesIntegrationTest {

	private static final Path ADMIN_FILE = Path.of("build/test-admins.txt");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private PlayerService playerService;

	@Autowired
	private TournamentRunService run;

	private String adminToken;
	private String name;

	@BeforeEach
	void setUp() throws Exception {
		String admin = "Op" + UUID.randomUUID().toString().substring(0, 8);
		adminToken = tokenFor(UUID.randomUUID(), admin);
		name = "t_" + UUID.randomUUID().toString().substring(0, 8);
		Files.createDirectories(ADMIN_FILE.getParent());
		Files.writeString(ADMIN_FILE, admin + "\n");
		Files.setLastModifiedTime(ADMIN_FILE, FileTime.from(Instant.now().plusSeconds((long) (Math.random() * 1000) + 1)));
	}

	@AfterEach
	void cleanUp() throws Exception {
		mockMvc.perform(post("/admin/tournaments/" + name + "/cancel").header("Authorization", adminToken));
		mockMvc.perform(delete("/admin/tournaments/" + name).header("Authorization", adminToken));
	}

	private String tokenFor(UUID uuid, String username) {
		playerService.recordConnection(uuid, username);
		return "Bearer " + jwtService.issueAccessToken(uuid, username);
	}

	private ResultActions create(String dates) throws Exception {
		return mockMvc.perform(post("/admin/tournaments").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"%s\",\"kind\":\"SWISS\",\"best_of\":1,\"format_id\":\"gen9ou\"%s}".formatted(name, dates)));
	}

	private ResultActions schedule(String body) throws Exception {
		return mockMvc.perform(post("/admin/tournaments/" + name + "/schedule").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private void addPlayer() throws Exception {
		String username = "Pl" + UUID.randomUUID().toString().substring(0, 8);
		playerService.recordConnection(UUID.randomUUID(), username);
		mockMvc.perform(post("/admin/tournaments/" + name + "/players").header("Authorization", adminToken)
				.contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\"}".formatted(username)))
				.andExpect(status().isOk());
	}

	private ResultActions show() throws Exception {
		return mockMvc.perform(get("/tournaments/" + name).header("Authorization", adminToken));
	}

	private static String at(long minutes) {
		return Instant.now().plus(minutes, ChronoUnit.MINUTES).toString();
	}

	@Test
	void datesOutOfOrderAreRefused() throws Exception {
		create(",\"registration_opens_at\":\"%s\",\"registration_closes_at\":\"%s\"".formatted(at(60), at(30)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error_code").value("ERROR_TOURNAMENT_INVALID_DATES"));
		create(",\"check_in_opens_at\":\"%s\",\"check_in_closes_at\":\"%s\"".formatted(at(60), at(60)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void theCheckInOpensAndTheTournamentStartsAtTheirDates() throws Exception {
		create(",\"check_in_opens_at\":\"%s\",\"check_in_closes_at\":\"%s\"".formatted(at(-1), at(60)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REGISTRATION"))
				.andExpect(jsonPath("$.check_in_closes_at").isNotEmpty());
		run.datedSteps();
		show().andExpect(jsonPath("$.status").value("CHECK_IN"));
		// Added by an admin during the check-in: present.
		addPlayer();
		addPlayer();
		schedule("{\"check_in_closes_at\":\"%s\"}".formatted(at(-1))).andExpect(status().isOk());
		run.datedSteps();
		show().andExpect(jsonPath("$.status").value("RUNNING"))
				.andExpect(jsonPath("$.current_round").value(1));
	}

	@Test
	void aReadableNameGivesTheIdAndTwinsGetASuffix() throws Exception {
		String title = "Coupe Test " + name.substring(2);
		String expected = TournamentService.slug(title);
		String body = "{\"display_name\":\"%s\",\"kind\":\"BRACKET\",\"best_of\":3,\"format_id\":\"gen9ou\"}".formatted(title);
		mockMvc.perform(post("/admin/tournaments").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(expected))
				.andExpect(jsonPath("$.display_name").value(title));
		mockMvc.perform(post("/admin/tournaments").header("Authorization", adminToken).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value(expected + "_2"));
		for (String id : new String[] { expected, expected + "_2" }) {
			mockMvc.perform(post("/admin/tournaments/" + id + "/cancel").header("Authorization", adminToken));
			mockMvc.perform(delete("/admin/tournaments/" + id).header("Authorization", adminToken));
		}
	}

	@Test
	void tooFewPlayersLeaveItInTheCheckInAndDatesCanBeCleared() throws Exception {
		create(",\"check_in_opens_at\":\"%s\",\"check_in_closes_at\":\"%s\"".formatted(at(-2), at(-1)))
				.andExpect(status().isCreated());
		run.datedSteps();
		show().andExpect(jsonPath("$.status").value("CHECK_IN"));
		run.datedSteps(); // not retried, nothing changes
		show().andExpect(jsonPath("$.status").value("CHECK_IN"));
		schedule("{\"clear\":[\"check_in_closes_at\"]}").andExpect(status().isOk())
				.andExpect(jsonPath("$.check_in_closes_at").value(nullValue()));
	}
}
