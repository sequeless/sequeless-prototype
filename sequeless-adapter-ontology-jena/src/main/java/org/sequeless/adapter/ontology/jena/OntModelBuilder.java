package org.sequeless.adapter.ontology.jena;

import java.io.InputStream;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Parses a Turtle stream into a Jena {@link Model} and wraps it as an {@link OntModel} under a
 * given {@link ReasonerSetting}, resolving {@code owl:imports} against {@link
 * ImportResolver#withBundledMeta()} — per {@link org.apache.jena.ontapi.GraphRepository}'s
 * documented overload F30 confirmed present: {@code OntModelFactory.createModel(Graph,
 * OntSpecification, GraphRepository)}.
 *
 * <p><b>Deliberately does no validation.</b> Neither reasoner-backed consistency checking ({@link
 * ConsistencyChecker}'s job) nor reserved-{@code sq:}-term rejection ({@link SqVocabulary}'s job,
 * wired in by {@link JenaOntologyPort}) happens here — this class only turns bytes into a queryable
 * {@link OntModel}, however malformed the result might turn out to be. That separation is what lets
 * {@link JenaOntologyPort#fromDocument} build eagerly without throwing (F20): building always
 * succeeds as long as the Turtle itself parses, and every judgement about whether the result is
 * *usable* is deferred to whoever asks for a snapshot.
 *
 * <p><b>Unresolved {@code owl:imports} do not throw.</b> Verified empirically against Jena 6.1.0,
 * contrary to the design assumption on record before this task ran the probe: {@code
 * GraphRepository.createGraphDocumentRepositoryMem()} auto-vivifies an empty graph for any IRI
 * {@code get()} is asked for that it does not already hold, so {@code
 * OntModelFactory.createModel(...)} never throws for an import this adapter cannot resolve — it
 * silently treats the unresolved ontology as contributing nothing. Consequently the "convert an
 * unresolved import into an {@code ERROR} issue" behaviour the step plan calls for cannot be
 * implemented as a caught exception; {@link JenaOntologyPort} instead compares {@code
 * model.getID().imports()} against the one IRI {@link ImportResolver} actually registers ({@link
 * ImportResolver#SQ_META_IRI}) after the fact. See {@code JenaOntologyPort}'s javadoc.
 *
 * <p>Package-private: only {@link JenaOntologyPort} calls this.
 */
final class OntModelBuilder {

    /**
     * A synthetic base IRI supplied to the Turtle parser so a fixture or source document that
     * happens to use a relative IRI does not fail to parse for lack of one; every fixture and the
     * app's own reference ontology uses only absolute IRIs, so this value is never actually resolved
     * against in practice — it exists purely as a defensive fallback.
     */
    private static final String FALLBACK_BASE_IRI = "urn:sequeless:jena-adapter:loaded-ontology";

    private OntModelBuilder() {}

    /**
     * @param turtle a stream positioned at the start of a Turtle document; the caller retains
     *     ownership and must close it — this method reads it fully but does not close it
     * @param reasoner which {@link ReasonerSetting} to build the model under
     * @return a non-{@code null} {@link OntModel}, however inconsistent or malformed its content
     *     may turn out to be; no validation has been performed
     */
    static OntModel build(InputStream turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(rdfModel, turtle, FALLBACK_BASE_IRI, Lang.TURTLE);
        return OntModelFactory.createModel(
            rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }
}
