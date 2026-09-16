/**
 * The mechanical contract test for {@link org.sequeless.spi.ontology.OntologyPort}.
 *
 * <p>{@link org.sequeless.testkit.ontology.OntologyContract} asserts every clause of the
 * behavioural contract documented on {@code OntologyPort} itself, against any implementation
 * supplied by a subclass via {@code portFor(OntologyDocument)}. It intentionally never inspects
 * which supertypes a type reports or whether an inverse property is visible — only null-safety,
 * determinism, the failed-mutation and round-trip guarantees, and the throw-on-inconsistency
 * behaviour — so that both a trivial non-reasoning port and a full OWL reasoner can pass it
 * unmodified. This module's own {@code InMemoryOntologyPortContractTest} (in {@code
 * src/test/java}) is the standing proof of that property.
 */
package org.sequeless.testkit.ontology;
