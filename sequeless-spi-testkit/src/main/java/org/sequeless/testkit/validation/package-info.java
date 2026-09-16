/**
 * The mechanical contract test for {@link org.sequeless.spi.validation.ValidationPort}.
 *
 * <p>{@link org.sequeless.testkit.validation.ValidationContract} asserts every clause of the
 * behavioural contract documented on {@code ValidationPort} itself, against any implementation a
 * subclass supplies for a given ontology document via {@code fixtureFor(OntologyDocument)}. Unlike
 * {@link org.sequeless.testkit.object.ObjectStoreContract}, this contract is not ontology-agnostic:
 * building a {@code MetaModelSnapshot} from Turtle requires an OWL/RDF library this testkit
 * deliberately does not depend on, so all snapshot-building work is delegated to whichever
 * adapter's own contract test extends this class. Assertions here are limited to null-safety,
 * determinism, the "unmodifiable, non-null list" result shape, and two representative violations
 * against the reference-domain ontology's own SHACL shapes ({@code ex:TaskShape}, {@code
 * ex:PersonShape}) — never an exhaustive enumeration of every shape a real ontology might declare.
 */
package org.sequeless.testkit.validation;
