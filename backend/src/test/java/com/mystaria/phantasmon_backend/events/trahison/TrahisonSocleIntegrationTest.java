package com.mystaria.phantasmon_backend.events.trahison;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.mystaria.phantasmon_backend.TestcontainersConfiguration;
import com.mystaria.phantasmon_backend.websocket.SessionRegistry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Trahison's foundation (T0.1): the table exists, the handler takes its messages and answers a Ping with a Pong. */
@Import(TestcontainersConfiguration.class)
// Same properties as MirrorHubIntegrationTest: one shared Spring context.
@SpringBootTest(properties = { "phantasmon.logging.enabled=false", "phantasmon.admin.file=build/test-admins.txt" })
class TrahisonSocleIntegrationTest {

	@Autowired
	private TrahisonWsHandler handler;

	@Autowired
	private SessionRegistry sessionRegistry;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void theGamesTableExistsWithItsColumns() {
		var columns = jdbc.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'trahison_games'", String.class);
		assertThat(columns).containsExactlyInAnyOrder("id", "hub", "state", "created_at", "updated_at", "ended_at");
	}

	@Test
	void theHandlerTakesOnlyItsOwnMessages() {
		assertThat(handler.handles("EventsTrahisonPing")).isTrue();
		assertThat(handler.handles("EventsTrahisonAction")).isTrue();
		assertThat(handler.handles("EventsMirrorBlocks")).isFalse();
		assertThat(handler.handles("EventsTournamentChanged")).isFalse();
	}

	@Test
	void aPingIsAnsweredByAPongWithTheSameNonce() throws Exception {
		UUID player = UUID.randomUUID();
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.isOpen()).thenReturn(true);
		sessionRegistry.register(player, session);
		try {
			handler.handle(player, "EventsTrahisonPing", Map.of("nonce", "abc"));
			ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
			verify(session).sendMessage(sent.capture());
			JsonNode json = objectMapper.readTree(sent.getValue().getPayload());
			assertThat(json.get("type").asString()).isEqualTo("EventsTrahisonPong");
			assertThat(json.get("data").get("nonce").asString()).isEqualTo("abc");
		} finally {
			sessionRegistry.unregister(player, session);
		}
	}
}
