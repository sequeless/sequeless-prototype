/**
 * The ontology port: the outbound port that gives the application its type system, and the
 * vocabulary of consistency reporting, documents, and formats it is expressed in terms of.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside
 * the JDK ({@code java.*}) except {@link org.sequeless.spi.Scope} from its parent package and
 * {@link org.sequeless.spi.meta.MetaModelSnapshot} from its sibling {@code org.sequeless.spi.meta}
 * package. {@link org.sequeless.spi.ontology.OntologyPort} is the port interface; {@link
 * org.sequeless.spi.ontology.OntologyDocument}, {@link org.sequeless.spi.ontology.OntologyFormat},
 * {@link org.sequeless.spi.ontology.ImportMode}, and {@link org.sequeless.spi.ontology.ImportReport}
 * are its request and response vocabulary. {@link org.sequeless.spi.ontology.OntologyReport} (with
 * its {@link org.sequeless.spi.ontology.OntologyIssue} findings and {@link
 * org.sequeless.spi.ontology.Severity} levels) is the consistency-reporting vocabulary shared by
 * both {@link org.sequeless.spi.meta.MetaModelSnapshot} and {@link
 * org.sequeless.spi.ontology.OntologyException}, the unchecked exception {@link
 * org.sequeless.spi.ontology.OntologyPort#snapshot} and {@code reload} throw when the ontology they
 * would otherwise return is inconsistent. The full behavioural contract every implementation must
 * satisfy is documented on {@link org.sequeless.spi.ontology.OntologyPort} itself, and asserted
 * mechanically by {@code sequeless-spi-testkit}'s {@code OntologyContract}.
 */
package org.sequeless.spi.ontology;
