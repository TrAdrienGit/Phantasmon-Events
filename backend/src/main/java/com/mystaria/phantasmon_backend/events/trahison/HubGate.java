package com.mystaria.phantasmon_backend.events.trahison;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** What the betrayal game asks of the hubs (the core's hub service and repository), kept behind one seam. */
public interface HubGate {

	/** The canonical name of the mirror hub with this name (any case), if there is one. */
	Optional<String> resolve(String hubName);

	/** Whether the account is in a hub right now. */
	boolean isMember(UUID account);

	/** Everyone in the same hub as this member, them included; empty if they are in none. */
	Set<UUID> membersWith(UUID account);
}
