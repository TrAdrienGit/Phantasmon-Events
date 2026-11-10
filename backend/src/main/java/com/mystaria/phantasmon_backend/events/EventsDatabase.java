package com.mystaria.phantasmon_backend.events;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import lombok.extern.slf4j.Slf4j;

/**
 * Phantasmon Events' own tables (D-41): migrated by a Flyway of its own ({@code db/events}, history in
 * {@code flyway_schema_history_events}) right after the core's, and before JPA starts. A database without Events never
 * gets them. On a database that had them from the core (V18-V19, V22-V24), the baseline plus {@code IF NOT EXISTS}
 * statements change nothing.
 *
 * <p>No {@code Flyway} bean is exposed: Spring Boot would then skip the core's own Flyway.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class EventsDatabase {

	static final String HISTORY_TABLE = "flyway_schema_history_events";

	/** Runs Events' migrations; a bean so that JPA waits for it. */
	public static final class Migration {
		Migration(DataSource dataSource) {
			var result = Flyway.configure()
					.dataSource(dataSource)
					.locations("classpath:db/events")
					.table(HISTORY_TABLE)
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load()
					.migrate();
			log.info("Phantasmon Events database: {} migration(s) applied, schema version {}", result.migrationsExecuted,
					result.targetSchemaVersion);
		}
	}

	@Bean
	@DependsOn("flywayInitializer")
	Migration eventsDatabaseMigration(DataSource dataSource) {
		return new Migration(dataSource);
	}

	/** JPA (Events' entities) starts only once Events' tables exist. */
	@Bean
	static EntityManagerFactoryDependsOnPostProcessor eventsDatabaseBeforeJpa() {
		return new EntityManagerFactoryDependsOnPostProcessor("eventsDatabaseMigration");
	}
}
