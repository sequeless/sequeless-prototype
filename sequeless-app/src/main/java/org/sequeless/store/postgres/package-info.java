/**
 * The host's Postgres wiring (spec 18, U11 and U12a): the Spring autoconfiguration, its properties, the adapter descriptor,
 * the migrating {@code DataSource} and the composed migration. They run the released sequeless-store Postgres adapter, whose
 * Command classes are in package {@code org.sequeless.store.postgres} in {@code store-postgres-jsonb-adapter}.
 *
 * <p>{@link org.sequeless.store.postgres.ComposedMigrations} runs the Command set and the Query set in the order the search
 * trigger needs. {@code PostgresPersistenceAutoConfiguration} wires the store's adapter into the Spring context. The ontology
 * document store ({@code PostgresOntologyDocumentStore}) stays in {@code sequeless-adapter-persistence-postgres} until spec 18
 * unit U14.
 */
package org.sequeless.store.postgres;
