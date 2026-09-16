package org.sequeless.adapter.ontology.jena;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code sequeless.ontology.*} configuration namespace this adapter reads at startup:
 * which adapter to activate ({@link #getAdapter()}, consulted only by {@link
 * JenaOntologyAutoConfiguration}'s {@code @ConditionalOnProperty}, not by this class itself), which
 * reasoning level to build the model under ({@link #getReasoner()}), and where the ontology document
 * lives ({@link #getSource()}, passed straight through to {@link
 * JenaOntologyPort#fromSource(String, String, ReasonerSetting)}).
 *
 * <h2>Why {@link ReasonerSetting#OWL} is the default</h2>
 *
 * <p>{@link #reasoner} defaults to {@link ReasonerSetting#OWL} because it is empirically the
 * <em>only</em> one of the three settings that detects the project's inconsistency fixture (see
 * {@code sequeless-spi-testkit}'s {@code inconsistent.ttl} and the adapter's {@code
 * ConsistencyCheckerTest}): {@link ReasonerSetting#RDFS}-only inference has no notion of {@code
 * owl:disjointWith} at all, so the very inconsistency the fixture is built to exercise is invisible
 * to it and it reports the document as valid; {@link ReasonerSetting#NONE} has no reasoner attached
 * to the model whatsoever, so there is nothing to run {@code validate()} against in the first place.
 * Shipping either of those as the default would mean a freshly started application silently accepts
 * an ontology it should have rejected at startup — defaulting to {@code owl} is what makes "detect
 * an inconsistent ontology" an actual guarantee rather than something that only holds if an operator
 * remembers to opt into it.
 */
@ConfigurationProperties("sequeless.ontology")
public class JenaOntologyProperties {

    /**
     * Which {@code OntologyPort} adapter to activate; must equal {@code "jena"} for {@link
     * JenaOntologyAutoConfiguration} to wire this adapter's bean in at all. Read only by that
     * auto-configuration's {@code @ConditionalOnProperty}, never by this class.
     */
    private String adapter;

    /**
     * Which reasoning level to build the Jena model under. Defaults to {@link ReasonerSetting#OWL}
     * — see this class's javadoc for why.
     */
    private ReasonerSetting reasoner = ReasonerSetting.OWL;

    /**
     * The {@code classpath:} or {@code file:} location of the ontology document to load, passed
     * straight through to {@link JenaOntologyPort#fromSource(String, String, ReasonerSetting)}.
     */
    private String source;

    /**
     * @return which {@code OntologyPort} adapter to activate
     */
    public String getAdapter() {
        return adapter;
    }

    /**
     * @param adapter which {@code OntologyPort} adapter to activate
     */
    public void setAdapter(String adapter) {
        this.adapter = adapter;
    }

    /**
     * @return which reasoning level to build the Jena model under
     */
    public ReasonerSetting getReasoner() {
        return reasoner;
    }

    /**
     * @param reasoner which reasoning level to build the Jena model under
     */
    public void setReasoner(ReasonerSetting reasoner) {
        this.reasoner = reasoner;
    }

    /**
     * @return the {@code classpath:} or {@code file:} location of the ontology document to load
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source the {@code classpath:} or {@code file:} location of the ontology document to
     *     load
     */
    public void setSource(String source) {
        this.source = source;
    }
}
