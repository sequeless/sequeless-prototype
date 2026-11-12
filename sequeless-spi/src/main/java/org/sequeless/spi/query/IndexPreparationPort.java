package org.sequeless.spi.query;

import org.sequeless.kernel.identity.Scope;
import org.sequeless.ontology.schema.api.OntologyVersion;
import org.sequeless.ontology.schema.api.Schema;

/**
 * The outbound port through which the host prepares whatever index structures an implementation
 * needs in order to answer {@link BrowsePort#browse} and {@link ReadPort#read} efficiently. It is
 * a driven port: the host calls it after every ontology activation, and adapters implement it.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. It is
 * asserted by the adapter's own test suite, not by an advisory javadoc check.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument must be non-{@code null}. A conforming implementation
 *       throws {@link NullPointerException} when any argument, including {@code version}, is
 *       {@code null}.
 *   <li><b>Version is not assumed to match the schema.</b> The {@code version} argument is supplied
 *       by the caller and is not checked against {@code schema}. An implementation must not assume
 *       that {@code version.equals(schema.ontologyVersion())}; it may use {@code schema} alone to
 *       decide what to prepare.
 *   <li><b>{@link #prepare} is idempotent.</b> The host calls it after every ontology activation
 *       (import, reload and startup), so it is called repeatedly with equivalent {@link Schema}s.
 *       A conforming implementation must not fail, error, or accumulate duplicate index structures
 *       on a repeat call.
 *   <li><b>{@link #prepare} is a no-op for a schema with no indexing needs.</b> A {@link Schema}
 *       whose types declare no {@code sq:indexed} and no {@code sq:searchable} properties must not
 *       cause {@link #prepare} to fail; it simply has nothing to prepare.
 * </ul>
 */
public interface IndexPreparationPort {

    /**
     * Prepares whatever index structures this implementation needs in order to answer browse and
     * read requests efficiently for the types described in {@code schema}, for example one index
     * per {@code sq:indexed} property and a full-text index over every {@code sq:searchable}
     * property. See the interface-level javadoc for the full contract this method must satisfy.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param version the ontology version the caller is activating; must not be {@code null}; not
     *     checked against {@code schema}
     * @param schema the type system to prepare indexes for; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    void prepare(Scope scope, OntologyVersion version, Schema schema);
}
