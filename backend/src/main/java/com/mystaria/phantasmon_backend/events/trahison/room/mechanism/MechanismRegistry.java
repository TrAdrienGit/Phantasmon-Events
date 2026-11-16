package com.mystaria.phantasmon_backend.events.trahison.room.mechanism;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Every {@link Mechanism} bean by id. */
@Component
public class MechanismRegistry {

	private final Map<String, Mechanism> byId = new LinkedHashMap<>();

	public MechanismRegistry(List<Mechanism> mechanisms) {
		for (Mechanism m : mechanisms) {
			if (byId.put(m.id(), m) != null) {
				throw new IllegalStateException("two mechanisms have the id " + m.id());
			}
		}
	}

	public Optional<Mechanism> find(String id) {
		return Optional.ofNullable(byId.get(id));
	}

	public Mechanism get(String id) {
		Mechanism m = byId.get(id);
		if (m == null) {
			throw new IllegalArgumentException("unknown mechanism " + id);
		}
		return m;
	}

	/** Every mechanism, sorted by id. */
	public List<Mechanism> all() {
		return byId.values().stream().sorted(java.util.Comparator.comparing(Mechanism::id)).toList();
	}

	public Set<String> ids() {
		return Set.copyOf(byId.keySet());
	}
}
