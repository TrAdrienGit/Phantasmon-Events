package com.mystaria.phantasmon_backend.events.tournament;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tournament (Phantasmon Events, plan-tournois.md): weekly Swiss rounds ({@code SWISS}) or a double elimination
 * bracket ({@code BRACKET}), played in Ghost battles of one Smogon format, each match in best of 1, 3 or 5.
 * {@code REGISTRATION} → {@code CHECK_IN} → {@code RUNNING} → {@code FINISHED} (or {@code CANCELLED}): the check-in
 * opens and the tournament starts by themselves at their dates when set (2026-11-11), or when an admin says so.
 */
@Entity
@Table(name = "tournaments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tournament {

	public static final String SWISS = "SWISS";
	public static final String BRACKET = "BRACKET";

	public static final String REGISTRATION = "REGISTRATION";
	public static final String CHECK_IN = "CHECK_IN";
	public static final String RUNNING = "RUNNING";
	public static final String FINISHED = "FINISHED";
	public static final String CANCELLED = "CANCELLED";

	@Id
	private UUID uuid;

	@Column(nullable = false, length = 32)
	private String name;

	@Column(nullable = false, length = 8)
	private String kind;

	@Column(name = "best_of", nullable = false)
	private short bestOf;

	@Column(name = "format_id", nullable = false, length = 64)
	private String formatId;

	@Column(nullable = false, length = 12)
	private String status = REGISTRATION;

	@Column(name = "starts_at")
	private Instant startsAt;

	@Column(name = "registration_opens_at")
	private Instant registrationOpensAt;

	@Column(name = "registration_closes_at")
	private Instant registrationClosesAt;

	/** The check-in opens by itself at this time (null: by an admin only). */
	@Column(name = "check_in_opens_at")
	private Instant checkInOpensAt;

	/** The check-in closes and the tournament starts by itself at this time (null: by an admin only). */
	@Column(name = "check_in_closes_at")
	private Instant checkInClosesAt;

	/** Swiss: the rounds planned (computed at the start, changed by the admins); null before. */
	@Column(name = "planned_rounds")
	private Short plannedRounds;

	/** The round announced last, 0 before the first. */
	@Column(name = "current_round", nullable = false)
	private short currentRound;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Tournament(UUID uuid, String name, String kind, int bestOf, String formatId, UUID createdBy) {
		this.uuid = uuid;
		this.name = name;
		this.kind = kind;
		this.bestOf = (short) bestOf;
		this.formatId = formatId;
		this.createdBy = createdBy;
	}

	public boolean over() {
		return FINISHED.equals(status) || CANCELLED.equals(status);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}
}
