/**
 * The default {@code ObjectStorePort} adapter, backed by PostgreSQL 18 JSONB storage: {@link
 * org.sequeless.adapter.persistence.postgres.PostgresObjectStore} for {@code sq_object} and
 * {@code sq_outbox}, {@link org.sequeless.adapter.persistence.postgres.PostgresOntologyDocumentStore}
 * for {@code sq_ontology_document}, and a tagged-JSON {@link
 * org.sequeless.adapter.persistence.postgres.ValueJsonCodec} for property values. The schema is
 * owned by this module's own two Flyway migration sets, run independently of Spring Boot's Flyway
 * autoconfiguration: the Command set under {@code classpath:db/migration/sequeless/postgres}, and the
 * Query set (index registries and the GIN index) under {@code
 * classpath:db/migration/sequeless/postgres-query}.
 *
 * <p>Spring auto-configuration wiring ({@code PostgresPersistenceAutoConfiguration}) lands in a
 * later task; this package can already be used standalone by constructing {@link
 * org.sequeless.adapter.persistence.postgres.PostgresObjectStore} directly from a hand-built
 * {@code JdbcClient} and {@code PlatformTransactionManager}.
 */
package org.sequeless.adapter.persistence.postgres;
