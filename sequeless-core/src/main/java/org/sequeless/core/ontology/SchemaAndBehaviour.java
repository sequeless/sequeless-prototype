package org.sequeless.core.ontology;

import java.util.Objects;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;

/**
 * The schema and behaviour halves of one ontology activation, read together.
 *
 * <p>Both halves carry the {@link org.sequeless.spi.meta.schema.OntologyVersion} of the activation
 * they came from. {@link #read} only returns a pair whose versions match, so one request never mixes
 * two activations.
 *
 * @param schema the schema half
 * @param behaviour the behaviour half, at the same version as {@code schema}
 */
public record SchemaAndBehaviour(Schema schema, Behaviour behaviour) {

    /**
     * @throws NullPointerException if either argument is {@code null}
     */
    public SchemaAndBehaviour {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(behaviour, "behaviour must not be null");
    }

    /**
     * Reads both halves for {@code tenant}. If their versions differ, reads both once more and
     * returns the second pair if its versions match.
     *
     * @param tenant the tenant whose ontology is read; must not be {@code null}
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @return a pair whose schema and behaviour share one ontology version
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalStateException if the versions still differ after the second read, which means
     *     the ontology was reloaded between the reads
     */
    public static SchemaAndBehaviour read(TenantId tenant, SchemaSource schemaSource, BehaviourSource behaviourSource) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");

        Schema schema = schemaSource.schema(tenant);
        Behaviour behaviour = behaviourSource.behaviour(tenant);
        if (sameVersion(schema, behaviour)) {
            return new SchemaAndBehaviour(schema, behaviour);
        }

        schema = schemaSource.schema(tenant);
        behaviour = behaviourSource.behaviour(tenant);
        if (sameVersion(schema, behaviour)) {
            return new SchemaAndBehaviour(schema, behaviour);
        }

        throw new IllegalStateException("Schema is at ontology version "
                + schema.ontologyVersion().value() + " and behaviour is at "
                + behaviour.ontologyVersion().value() + " after a retry; the ontology changed during the read");
    }

    private static boolean sameVersion(Schema schema, Behaviour behaviour) {
        return schema.ontologyVersion().equals(behaviour.ontologyVersion());
    }
}
