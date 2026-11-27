package org.sequeless.store.core.usecase;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.ObjectNotFoundException;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.api.TypeDefinition;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.store.api.AccessDecision;
import org.sequeless.store.api.Operation;
import org.sequeless.store.core.AuthorizationException;
import org.sequeless.store.core.api.TypeNotFoundException;
import org.sequeless.store.core.validation.TypeHierarchy;
import org.sequeless.store.spi.AuthorizationPort;

/**
 * The three checks every business-object use case runs before it touches a port: the type a request names resolves
 * against the current schema, the caller is authorized for the operation on the resolved type, and a stored object's
 * type is the requested type or a subtype of it.
 *
 * <p>The command use cases in this package and the host's query service both call these, so the logic is written once.
 * The class is public only for that call from the host. It holds no state, and nothing else in core is made public for
 * the query side.
 */
public final class BusinessObjectGuards {

    private BusinessObjectGuards() {}

    /**
     * Resolves a type by its short name, or else by its full IRI, against {@code schema}.
     *
     * @param schema the current schema; must not be {@code null}
     * @param nameOrIri the type's short name or full IRI; must not be {@code null}
     * @return the resolved type's definition
     * @throws TypeNotFoundException if no type in {@code schema} matches {@code nameOrIri}
     */
    public static TypeDefinition resolveType(Schema schema, String nameOrIri) {
        return schema.typeByName(nameOrIri)
                .or(() -> schema.type(nameOrIri))
                .orElseThrow(() -> new TypeNotFoundException(nameOrIri));
    }

    /**
     * Asks {@code authorizationPort} for a decision on {@code operation} against {@code resource}, and throws if it
     * denies.
     *
     * @param authorizationPort the port consulted on every call; must not be {@code null}
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code null}
     * @param operation the operation the request performs
     * @param resource the resolved type's IRI
     * @throws AuthorizationException if the port denies the operation
     */
    public static void authorize(
            AuthorizationPort authorizationPort, Scope scope, Operation operation, String resource) {
        AccessDecision decision = authorizationPort.decide(scope, operation, resource);
        if (!decision.allowed()) {
            throw new AuthorizationException(decision);
        }
    }

    /**
     * Throws if the stored object's type is neither {@code resolved} nor a subtype of it. An unrelated type is reported
     * as {@link ObjectNotFoundException}, the same as a missing object, so a caller cannot probe for other types.
     *
     * @param schema the schema both types are read from; must not be {@code null}
     * @param storedType the type the stored object has; must not be {@code null}
     * @param resolved the type the request resolved to; must not be {@code null}
     * @param id the object's id, for the exception; must not be {@code null}
     * @throws ObjectNotFoundException if {@code storedType} is not {@code resolved} or a subtype of it
     */
    public static void requireMatchingType(Schema schema, TypeRef storedType, TypeDefinition resolved, ObjectId id) {
        if (!TypeHierarchy.isSubtypeOf(schema, storedType.iri(), resolved.iri())) {
            throw new ObjectNotFoundException(id);
        }
    }
}
