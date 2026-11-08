/**
 * The ontology administration port: the outbound port that loads, reloads, exports and imports the
 * ontology, and the vocabulary of consistency reporting, documents, and formats it is expressed in
 * terms of.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) except {@link org.sequeless.kernel.identity.Scope} from its parent package.
 * It does not depend on any half of the ontology in {@code org.sequeless.spi.meta}. {@link
 * org.sequeless.spi.ontology.OntologyAdministration} is the port interface; {@link
 * org.sequeless.spi.ontology.OntologyDocument}, {@link org.sequeless.spi.ontology.OntologyFormat},
 * {@link org.sequeless.spi.ontology.ImportMode}, and {@link org.sequeless.spi.ontology.ImportReport}
 * are its request and response vocabulary. {@link org.sequeless.spi.ontology.OntologyReport} (with
 * its {@link org.sequeless.spi.ontology.OntologyIssue} findings and {@link
 * org.sequeless.spi.ontology.Severity} levels) is the consistency-reporting vocabulary shared by the
 * read ports, which live in the half packages, and by {@link
 * org.sequeless.spi.ontology.OntologyException}, the unchecked exception those read ports and {@link
 * org.sequeless.spi.ontology.OntologyAdministration#reload} throw when the ontology they would
 * otherwise return is inconsistent. The full behavioural contract every implementation must satisfy
 * is documented on {@link org.sequeless.spi.ontology.OntologyAdministration} itself, and asserted
 * mechanically by {@code sequeless-spi-testkit}'s {@code OntologyContract}.
 */
package org.sequeless.spi.ontology;
