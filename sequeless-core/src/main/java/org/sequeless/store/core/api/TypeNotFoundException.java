package org.sequeless.store.core.api;

/**
 * Thrown by {@code MetaModelService#describeType(org.sequeless.kernel.identity.Scope, String)} when
 * no type in the current schema matches the requested short name or IRI.
 *
 * <p>It sits in the {@code api} package with the other use case types, rather than at the {@code org.sequeless.store.core} root
 * like {@link org.sequeless.store.core.AuthorizationException}, because it is specific to this one use
 * case's contract rather than cross-cutting to every use case.
 */
public final class TypeNotFoundException extends RuntimeException {

    /**
     * @param nameOrIri the short name or IRI that could not be resolved to a type
     */
    public TypeNotFoundException(String nameOrIri) {
        super("No type found for '" + nameOrIri + "'");
    }
}
