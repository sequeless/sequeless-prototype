/**
 * The mechanical contract test for {@link org.sequeless.spi.ontology.OntologyAdministration} and
 * for the three read ports that describe the active ontology: {@link
 * org.sequeless.spi.meta.schema.SchemaSource}, {@link org.sequeless.spi.meta.behaviour.BehaviourSource}
 * and {@link org.sequeless.spi.meta.automation.AutomationSource}.
 *
 * <p>{@link org.sequeless.testkit.ontology.OntologyContract} asserts every clause of the
 * behavioural contracts documented on those four interfaces, against any implementation supplied
 * by a subclass via {@code portFor(OntologyDocument)}, which returns an {@link
 * org.sequeless.testkit.ontology.OntologyPorts}. It intentionally never inspects which supertypes a
 * type reports or whether an inverse property is visible — only null-safety, determinism, the
 * failed-mutation, round-trip and version guarantees, and the throw-on-inconsistency behaviour — so
 * that both a trivial non-reasoning port and a full OWL reasoner can pass it unmodified. This
 * module's own {@code InMemoryOntologyPortContractTest} (in {@code src/test/java}) is the standing
 * proof of that property.
 */
package org.sequeless.testkit.ontology;
