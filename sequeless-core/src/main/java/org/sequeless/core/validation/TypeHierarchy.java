package org.sequeless.core.validation;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.TypeRef;

/**
 * Walks {@link TypeDefinition#superTypes()} transitively against a {@link MetaModelSnapshot}, the
 * one place this walk is written so {@link StructuralValidator} (reference-target subtype
 * checking) and {@code DefaultBusinessObjectService} ({@code browse}'s "type plus its subtypes",
 * and the subtype-inclusive type match on {@code read}/{@code edit}/{@code delete}) do not each
 * hand-roll their own version and drift apart.
 *
 * <p>Stateless utility class: every method takes the {@link MetaModelSnapshot} it walks as a
 * parameter, so there is no per-call state to inject and thus nothing an instance would carry.
 */
public final class TypeHierarchy {

    private TypeHierarchy() {}

    /**
     * @param snapshot the snapshot whose {@link TypeDefinition#superTypes()} chains are walked;
     *     must not be {@code null}
     * @param candidateIri the IRI to test; must not be {@code null}
     * @param targetIri the IRI {@code candidateIri} must equal or transitively specialise; must not
     *     be {@code null}
     * @return {@code true} if {@code candidateIri} equals {@code targetIri}, or {@code targetIri}
     *     appears anywhere in {@code candidateIri}'s transitive {@link TypeDefinition#superTypes()}
     *     chain; {@code false} if {@code candidateIri} does not name a type in {@code snapshot} at
     *     all — a dangling reference is not this method's problem to raise
     * @throws NullPointerException if any argument is {@code null}
     */
    public static boolean isSubtypeOf(
        MetaModelSnapshot snapshot, String candidateIri, String targetIri) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(candidateIri, "candidateIri must not be null");
        Objects.requireNonNull(targetIri, "targetIri must not be null");

        if (candidateIri.equals(targetIri)) {
            return true;
        }
        return isSubtypeOf(snapshot, candidateIri, targetIri, new HashSet<>());
    }

    private static boolean isSubtypeOf(
        MetaModelSnapshot snapshot, String candidateIri, String targetIri, Set<String> visited) {
        if (!visited.add(candidateIri)) {
            return false;
        }
        Optional<TypeDefinition> candidate = snapshot.type(candidateIri);
        if (candidate.isEmpty()) {
            return false;
        }
        for (String superTypeIri : candidate.get().superTypes()) {
            if (superTypeIri.equals(targetIri)) {
                return true;
            }
            if (isSubtypeOf(snapshot, superTypeIri, targetIri, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param snapshot the snapshot to search; must not be {@code null}
     * @param targetIri the IRI whose type-and-subtypes set is wanted; must not be {@code null}
     * @return every type IRI in {@code snapshot} that is {@code targetIri} itself or a transitive
     *     subtype of it, wrapped as {@link TypeRef}, in {@link MetaModelSnapshot#types()} order — a
     *     type is trivially its own subtype under {@link #isSubtypeOf}, so {@code targetIri} itself
     *     is always included without a special case, including when it has no subtypes at all
     * @throws NullPointerException if either argument is {@code null}
     */
    public static Set<TypeRef> typeAndSubtypes(MetaModelSnapshot snapshot, String targetIri) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(targetIri, "targetIri must not be null");

        Set<TypeRef> result = new LinkedHashSet<>();
        for (TypeDefinition candidate : snapshot.types()) {
            if (isSubtypeOf(snapshot, candidate.iri(), targetIri)) {
                result.add(new TypeRef(candidate.iri()));
            }
        }
        return result;
    }
}
