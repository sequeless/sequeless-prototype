package org.sequeless.spi.ontology;

import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;

/**
 * The outbound port that gives the application its type system. Exactly one implementation is
 * wired into the running application at a time, selected by configuration property (see the app's
 * {@code PortRegistry}); per DR-03 the core never sees the ontology library an adapter is built on
 * — it asks this port for an immutable {@link MetaModelSnapshot} and works only with that.
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
 *   <li><b>{@link #snapshot(Scope)} is deterministic and side-effect-free.</b> Repeated calls with
 *       an equal {@code scope}, with no intervening {@link #reload(Scope)} or {@link
 *       #importDocument(Scope, OntologyDocument, ImportMode)} call, must return snapshots that are
 *       equal to one another, and calling it must not itself change what any other method
 *       subsequently returns. A caller may call it as many times as it likes without worrying it
 *       is mutating anything.
 *   <li><b>{@link #reload(Scope)} rebuilds from the current source.</b> It re-reads and re-validates
 *       the ontology from whatever backing source the adapter is configured with, independent of
 *       anything cached from a previous call. If the freshly built ontology is inconsistent, {@code
 *       reload} throws {@link OntologyException} and leaves the previously held snapshot as what a
 *       subsequent {@link #snapshot(Scope)} call returns — a failed reload must never leave the
 *       port in a state where {@code snapshot()} also fails or returns something built from the bad
 *       source.
 *   <li><b>Export and import round-trip.</b> For any {@code scope} and {@code format} such that
 *       {@link #export(Scope, OntologyFormat)} succeeds, passing the resulting {@link
 *       OntologyDocument} to {@link #importDocument(Scope, OntologyDocument, ImportMode)} with
 *       {@link ImportMode#REPLACE} must succeed and must produce a snapshot equal to the one in
 *       effect at the moment {@code export} was called. Serialising an ontology and reloading it
 *       from that serialisation must never lose information the snapshot exposes.
 *   <li><b>Inconsistency is always thrown, never returned.</b> {@link #snapshot(Scope)}, {@link
 *       #reload(Scope)}, and {@link #importDocument(Scope, OntologyDocument, ImportMode)} all throw
 *       {@link OntologyException} — carrying a non-{@link OntologyReport#consistent()} {@link
 *       OntologyReport} — rather than returning a snapshot or report that represents an
 *       inconsistent ontology as if it were usable. A caller can therefore treat "this method
 *       returned normally" as "the ontology is consistent" without inspecting anything further.
 * </ul>
 */
public interface OntologyPort {

    /**
     * Returns the current type system snapshot for {@code scope}, without re-reading the backing
     * source. See the interface-level javadoc for the full contract this method must satisfy,
     * including determinism and the throw-on-inconsistency behaviour.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return a non-null, immutable snapshot of the ontology currently loaded
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws OntologyException if the currently loaded ontology is inconsistent
     */
    MetaModelSnapshot snapshot(Scope scope);

    /**
     * Re-reads and re-validates the ontology from its backing source and returns the resulting
     * snapshot. See the interface-level javadoc for the full contract this method must satisfy,
     * including what happens to the previously held snapshot when this call fails.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return a non-null, immutable snapshot built fresh from the current backing source
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws OntologyException if the freshly read ontology is inconsistent; the previously held
     *     snapshot remains what a subsequent {@link #snapshot(Scope)} call returns
     */
    MetaModelSnapshot reload(Scope scope);

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
     * export/import round-trip guarantee and the throw-on-inconsistency behaviour.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param document the ontology document to import; must not be {@code null}
     * @param mode how {@code document} is applied to the ontology already loaded; must not be
     *     {@code null}
     * @return a non-null report describing the outcome of the import
     * @throws NullPointerException if any argument is {@code null}
     * @throws OntologyException if the resulting ontology is inconsistent; the previously held
     *     snapshot remains what a subsequent {@link #snapshot(Scope)} call returns
     */
    ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode);
}
