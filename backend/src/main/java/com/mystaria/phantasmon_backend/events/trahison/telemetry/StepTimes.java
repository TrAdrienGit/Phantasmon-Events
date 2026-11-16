package com.mystaria.phantasmon_backend.events.trahison.telemetry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mystaria.phantasmon_backend.events.trahison.room.RoomService;
import com.mystaria.phantasmon_backend.events.trahison.room.StepMedians;
import com.mystaria.phantasmon_backend.events.trahison.room.StepTimeSink;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T3.6 telemetry: stores the time of every step a group really solved ({@code trahison_step_times}) and answers the medians
 * per mechanism and per room. Plugs itself into {@link RoomService}.
 */
@Service
public class StepTimes implements StepTimeSink, StepMedians {

	private static final Logger LOG = LoggerFactory.getLogger(StepTimes.class);

	private final JdbcTemplate jdbc;
	private final ObjectMapper mapper;
	private final RoomService rooms;

	public StepTimes(JdbcTemplate jdbc, ObjectMapper mapper, RoomService rooms) {
		this.jdbc = jdbc;
		this.mapper = mapper;
		this.rooms = rooms;
	}

	@PostConstruct
	void plug() {
		rooms.setStepTimeSink(this);
	}

	@Override
	public void record(String roomId, int roomVersion, String stepId, String mechanism, JsonNode params, int seconds, int hintsGiven,
			int groupSize) {
		try {
			jdbc.update("""
					INSERT INTO trahison_step_times (room_id, room_version, step_id, mechanism, params, seconds, hints_given, group_size)
					VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
					""", roomId, roomVersion, stepId, mechanism, mapper.writeValueAsString(params), Math.max(0, seconds), hintsGiven,
					groupSize);
		} catch (RuntimeException ex) {
			LOG.warn("Trahison: could not record the time of step {} of room {}", stepId, roomId, ex);
		}
	}

	@Override
	public Map<String, Map<String, Median>> byRoomAndStep() {
		Map<String, Map<String, Median>> out = new LinkedHashMap<>();
		jdbc.query("""
				SELECT room_id, step_id, count(*), percentile_cont(0.5) WITHIN GROUP (ORDER BY seconds)
				FROM trahison_step_times GROUP BY room_id, step_id ORDER BY room_id, step_id
				""", rs -> {
			out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
					.put(rs.getString(2), new Median(rs.getInt(3), (int) Math.round(rs.getDouble(4))));
		});
		return out;
	}

	/** One line per mechanism: how many measures and their median, in seconds. */
	public List<Map<String, Object>> byMechanism() {
		return jdbc.query("""
				SELECT mechanism, count(*), percentile_cont(0.5) WITHIN GROUP (ORDER BY seconds), avg(hints_given)
				FROM trahison_step_times GROUP BY mechanism ORDER BY mechanism
				""", (rs, n) -> {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("mechanism", rs.getString(1));
			m.put("count", rs.getInt(2));
			m.put("median_seconds", (int) Math.round(rs.getDouble(3)));
			m.put("average_hints", Math.round(rs.getDouble(4) * 100) / 100.0);
			return m;
		});
	}

	/**
	 * One line per room: its steps with their count and median, and {@code median_seconds}, the sum of the medians of its
	 * steps measured at least once (what a group needs for the steps that were measured).
	 */
	public List<Map<String, Object>> byRoom() {
		Map<String, String> mechanismOf = new LinkedHashMap<>();
		jdbc.query("SELECT DISTINCT room_id, step_id, mechanism FROM trahison_step_times", rs -> {
			mechanismOf.put(rs.getString(1) + "/" + rs.getString(2), rs.getString(3));
		});
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		byRoomAndStep().forEach((room, steps) -> {
			Map<String, Object> r = new LinkedHashMap<>();
			r.put("room", room);
			int total = 0;
			List<Map<String, Object>> list = new java.util.ArrayList<>();
			for (Map.Entry<String, Median> e : steps.entrySet()) {
				Map<String, Object> s = new LinkedHashMap<>();
				s.put("step", e.getKey());
				s.put("mechanism", mechanismOf.get(room + "/" + e.getKey()));
				s.put("count", e.getValue().count());
				s.put("median_seconds", e.getValue().seconds());
				list.add(s);
				total += e.getValue().seconds();
			}
			r.put("median_seconds", total);
			r.put("steps", list);
			out.add(r);
		});
		return out;
	}
}
