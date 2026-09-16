package org.sequeless.adapter.ontology.jena;

import java.io.IOException;
import java.io.InputStream;
import org.apache.jena.graph.Graph;
import org.apache.jena.ontapi.GraphRepository;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Pre-registers this adapter's own bundled copy of {@code sq-meta.ttl} against its ontology IRI in
 * a fresh {@link GraphRepository}, so that {@code owl:imports <https://sequeless.dev/ns/meta>} —
 * which every ontology using the {@code sq:} vocabulary declares — resolves without ever touching
 * the network. Any other {@code owl:imports} target is left unregistered and fails inside {@code
 * OntModelFactory.createModel}; {@link OntModelBuilder} is responsible for catching that and
 * turning it into an {@code ERROR} {@code OntologyIssue} rather than letting a raw Jena exception
 * escape the adapter.
 *
 * <p>The bundled {@code sq-meta.ttl} here is a deliberate second copy of the canonical one in
 * {@code sequeless-spi-testkit}'s main resources: this adapter cannot depend on the testkit outside
 * test scope, so it ships its own. The two must stay byte-identical (see the drift-guard test
 * planned for a later task).
 */
final class ImportResolver {

    static final String SQ_META_IRI = "https://sequeless.dev/ns/meta";

    private static final String SQ_META_CLASSPATH_RESOURCE = "ontology/sq-meta.ttl";

    private ImportResolver() {}

    /**
     * @return a new, in-memory {@link GraphRepository} pre-populated with the bundled {@code
     *     sq-meta.ttl} graph under {@link #SQ_META_IRI}
     */
    static GraphRepository withBundledMeta() {
        GraphRepository repository = GraphRepository.createGraphDocumentRepositoryMem();
        repository.put(SQ_META_IRI, loadBundledMetaGraph());
        return repository;
    }

    private static Graph loadBundledMetaGraph() {
        try (InputStream in =
                ImportResolver.class.getClassLoader().getResourceAsStream(SQ_META_CLASSPATH_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                    "bundled resource missing from classpath: " + SQ_META_CLASSPATH_RESOURCE);
            }
            Model model = ModelFactory.createDefaultModel();
            RDFDataMgr.read(model, in, SQ_META_IRI, Lang.TURTLE);
            return model.getGraph();
        } catch (IOException e) {
            throw new IllegalStateException(
                "failed to read bundled resource: " + SQ_META_CLASSPATH_RESOURCE, e);
        }
    }
}
