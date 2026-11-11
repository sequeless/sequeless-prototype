package org.sequeless.core.usecase;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.core.AuthorizationException;
import org.sequeless.core.api.MetaModelService;
import org.sequeless.core.api.TypeDescription;
import org.sequeless.core.api.TypeNotFoundException;
import org.sequeless.core.ontology.SchemaAndBehaviour;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.api.OntologyReport;
import org.sequeless.ontology.behaviour.api.Behaviour;
import org.sequeless.ontology.behaviour.api.DerivationRule;
import org.sequeless.ontology.behaviour.spi.BehaviourSource;
import org.sequeless.ontology.schema.api.PropertyDefinition;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.sequeless.spi.authz.AccessDecision;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;

/**
 * Reference implementation of {@link MetaModelService}.
 *
 * <p>Consulting the injected {@link AuthorizationPort} is the entire point of this use case, the
 * same as it is for {@link DefaultWhoAmI}: it is never optional, never cached, and never
 * short-circuited. Every method calls the port exactly once and translates a denial into an
 * {@link AuthorizationException} carrying the port's own decision.
 *
 * <p>{@link #describeType(Scope, String)} resolves {@code nameOrIri} against the current schema
 * <em>before</em> authorizing, because authorization is performed against the resolved canonical
 * type IRI, which is only known once resolution has succeeded. Resolution tries {@link
 * Schema#typeByName(String)} first, falling back to {@link Schema#type(String)}; short names never
 * contain {@code #} or {@code /}, so there is no collision risk between the two lookups.
 */
public final class DefaultMetaModelService implements MetaModelService {

    private final SchemaSource schemaSource;
    private final BehaviourSource behaviourSource;
    private final org.sequeless.ontology.api.OntologyAdministration ontologyAdministration;
    private final AuthorizationPort authorizationPort;

    /**
     * @param schemaSource the port the schema half is read from; must not be {@code null}
     * @param behaviourSource the port the behaviour half is read from; must not be {@code null}
     * @param ontologyAdministration the port {@link #reload} goes through; must not be {@code null}
     * @param authorizationPort the port every call consults; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public DefaultMetaModelService(
            SchemaSource schemaSource,
            BehaviourSource behaviourSource,
            org.sequeless.ontology.api.OntologyAdministration ontologyAdministration,
            AuthorizationPort authorizationPort) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.behaviourSource = Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        this.ontologyAdministration =
                Objects.requireNonNull(ontologyAdministration, "ontologyAdministration must not be null");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort must not be null");
    }

    @Override
    public Schema schema(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope, Operation.BROWSE, AuthorizationPort.EVERYTHING);
        return schemaSource.schema(scope.tenantId());
    }

    @Override
    public Behaviour behaviour(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope, Operation.BROWSE, AuthorizationPort.EVERYTHING);
        return behaviourSource.behaviour(scope.tenantId());
    }

    @Override
    public TypeDescription describeType(Scope scope, String nameOrIri) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(nameOrIri, "nameOrIri must not be null");

        SchemaAndBehaviour halves = SchemaAndBehaviour.read(scope.tenantId(), schemaSource, behaviourSource);
        Schema schema = halves.schema();
        TypeDefinition type = schema.typeByName(nameOrIri)
                .or(() -> schema.type(nameOrIri))
                .orElseThrow(() -> new TypeNotFoundException(nameOrIri));

        authorize(scope, Operation.READ, type.iri());

        Behaviour behaviour = halves.behaviour();
        return new TypeDescription(type, behaviour.stateMachine(type.iri()), derivedProperties(type, behaviour));
    }

    @Override
    public OntologyReport reload(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        authorize(scope, Operation.ADMIN, AuthorizationPort.EVERYTHING);
        return ontologyAdministration.reload(scope);
    }

    /**
     * The derivation of each derived property of {@code type}, inherited ones included, in the order
     * of {@link TypeDefinition#properties()}.
     */
    private static Map<String, DerivationRule> derivedProperties(TypeDefinition type, Behaviour behaviour) {
        Map<String, DerivationRule> derivations = new LinkedHashMap<>();
        for (PropertyDefinition property : type.properties()) {
            Optional<DerivationRule> derivation = behaviour.derivation(property.iri());
            derivation.ifPresent(rule -> derivations.put(property.iri(), rule));
        }
        return derivations;
    }

    private void authorize(Scope scope, Operation operation, String resource) {
        AccessDecision decision = authorizationPort.decide(scope, operation, resource);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }
}
