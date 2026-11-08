package org.sequeless.adapter.ontology.jena;

/**
 * The two settings the Jena ontology adapter is configured with: which reasoning level to build the
 * model under, and where the ontology document lives. A plain value with no Spring binding, so a
 * non-Spring host can hold the same pair without {@code JenaOntologyProperties}.
 * {@code JenaOntologyProperties.settings()} produces one from the bound {@code sequeless.ontology.*}
 * configuration, and {@code JenaOntologyAutoConfiguration} unpacks it into the {@link
 * JenaOntologyPort} factory parameters. No factory takes this record directly.
 *
 * @param reasoner which reasoning level to build the model under
 * @param source the {@code classpath:} or {@code file:} location of the ontology document, as
 *     configured
 */
public record JenaOntologySettings(ReasonerSetting reasoner, String source) {}
