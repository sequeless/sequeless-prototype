package org.sequeless.core.api;

/**
 * Thrown by {@link MetaModelService#describeType(org.sequeless.spi.Scope, String)} when no type
 * in the current snapshot matches the requested short name or IRI.
 *
 * <p>This sits beside {@link MetaModelService} in the {@code api} package — mirroring {@link
 * WhoAmIResult} sitting beside {@link WhoAmI} — rather than at the {@code org.sequeless.core} root
 * like {@link org.sequeless.core.AuthorizationException}, because it is specific to this one use
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
