package org.sequeless.spi.ontology;

import org.sequeless.kernel.identity.Scope;

/**
 * The outbound port that loads, reloads, exports and imports the ontology. Exactly one
 * implementation is wired into the running application at a time, selected by configuration
 * property (see the app's {@code PortRegistry}); per DR-03 the core never sees the ontology library
 * an adapter is built on.
 *
 * <p>This port does not hand out the type system. It is read through three read ports, one per
 * half of an activation: {@code org.sequeless.spi.meta.schema.SchemaSource}, {@code
 * org.sequeless.spi.meta.behaviour.BehaviourSource} and {@code
 * org.sequeless.spi.meta.automation.AutomationSource}. Each returns the half of the version that is
 * active, and the three halves of one activation share one version.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is not
 * advisory: {@code sequeless-spi-testkit}'s {@code OntologyContract} (arriving in a later task)
 * asserts every clause of it mechanically against any port passed to it, and any adapter's own test
 * suite is expected to extend that contract. An implementation that violates a clause here is not a
 * valid adapter, regardless of what its own tests claim.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument to every method on this interface — {@code scope},
 *       {@code format}, {@code document}, and {@code mode} — must be non-{@code null}. A
 *       conforming implementation throws {@link NullPointerException} when any argument is {@code
 *       null}. It must never substitute a default or treat a null argument as "use the current
 *       state": missing input is a programming error in the caller, not a fact about the ontology.
 *   <li><b>{@link #reload(Scope)} rebuilds from the current source.</b> It re-reads and re-validates
 *       the ontology from whatever backing source the adapter is configured with, independent of
 *       anything cached from a previous call. If the freshly built ontology is inconsistent, {@code
 *       reload} throws {@link OntologyException}, and the halves that were active before the call
 *       remain what the read ports return.
 *   <li><b>Export and import round-trip.</b> For any {@code scope} and {@code format} such that
 *       {@link #export(Scope, OntologyFormat)} succeeds, passing the resulting {@link
 *       OntologyDocument} to {@link #importDocument(Scope, OntologyDocument, ImportMode)} with
 *       {@link ImportMode#REPLACE} must succeed and must produce halves equal to the ones in effect
 *       at the moment {@code export} was called. "Equal" here means equal in everything except
 *       {@code ontologyVersion}. Serialising an ontology and reloading it from that serialisation
 *       must never lose information the halves expose.
 *   <li><b>Failure keeps the previous halves.</b> A failed reload or import leaves the previous
 *       halves, with their version, as what the read ports return. A failed call never leaves the
 *       read ports failing, or returning halves built from the bad source.
 *   <li><b>Versions.</b> A successful import gives the halves a strictly greater {@code
 *       ontologyVersion} than the one they had before. A successful reload gives a version that is
 *       not lower than that one. All three halves of one activation share one version.
 *   <li><b>Inconsistency is always thrown, never returned.</b> {@link #reload(Scope)} and {@link
 *       #importDocument(Scope, OntologyDocument, ImportMode)} both throw {@link OntologyException}
 *       — carrying a non-{@link OntologyReport#consistent()} {@link OntologyReport} — rather than
 *       returning a report that represents an inconsistent ontology as if it were usable. A caller
 *       can therefore treat "this method returned normally" as "the ontology is consistent" without
 *       inspecting anything further.
 * </ul>
 */
public interface OntologyAdministration {

    /**
     * Re-reads and re-validates the ontology from its backing source and activates the result. See
     * the interface-level javadoc for the full contract this method must satisfy, including what
     * happens to the previously active halves when this call fails.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return a non-null report of the freshly activated ontology
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws OntologyException if the freshly read ontology is inconsistent; the previously active
     *     halves remain what the read ports return
     */
    OntologyReport reload(Scope scope);

    /**
     * Serialises the ontology currently loaded for {@code scope} into {@code format}. See the
     * interface-level javadoc for the round-trip guarantee this method's result must satisfy
     * together with {@link #importDocument(Scope, OntologyDocument, ImportMode)}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param format the serialisation to export into; must not be {@code null}
     * @return a non-null document containing the serialised ontology
     * @throws NullPointerException if either argument is {@code null}
     * @throws OntologyException if the currently loaded ontology is inconsistent
     */
    OntologyDocument export(Scope scope, OntologyFormat format);

    /**
     * Applies {@code document} to the ontology for {@code scope} according to {@code mode}. See the
     * interface-level javadoc for the full contract this method must satisfy, including the
     * export/import round-trip guarantee, the version rule and the throw-on-inconsistency behaviour.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param document the ontology document to import; must not be {@code null}
     * @param mode how {@code document} is applied to the ontology already loaded; must not be
     *     {@code null}
     * @return a non-null report describing the outcome of the import
     * @throws NullPointerException if any argument is {@code null}
     * @throws OntologyException if the resulting ontology is inconsistent; the previously active
     *     halves remain what the read ports return
     */
    ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode);
}
