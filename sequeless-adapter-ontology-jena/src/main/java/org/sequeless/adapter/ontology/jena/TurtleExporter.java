package org.sequeless.adapter.ontology.jena;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;

/**
 * Serialises {@link OntModel#getBaseModel()} — never {@code model} itself or any reasoner-expanded
 * union view — to Turtle. Using the base model is not a style preference: verified empirically that
 * the base model for the reference fixture carries 70 triples while the {@code owl} reasoner's
 * union view carries several times that many inferred statements; exporting the union would mean an
 * {@link org.sequeless.spi.ontology.OntologyPort#importDocument} of a previously exported document
 * re-imports the reasoner's own inferences as if they had been asserted by the source document,
 * silently growing the ontology on every export/import cycle.
 *
 * <p><b>Reproducibility.</b> {@link Model#write} on the base model directly delegates to Jena's
 * default Turtle writer, which groups triples by subject in whatever order its backing graph
 * happens to iterate — not guaranteed stable, and observed empirically to reorder blocks between
 * unrelated changes to the graph. This class instead sorts every statement by its {@code
 * Statement#toString()} form before writing with {@link RDFFormat#TURTLE_FLAT} (no subject
 * grouping, one triple per line, in exactly the sorted order given) — verified empirically to make
 * two exports of the *same* model byte-identical. Two independent builds of the *same source text*
 * can still differ at the byte level solely because of blank-node id renumbering (Jena assigns
 * fresh internal ids per parse), which sorts differently; this is a Jena limitation, not something
 * this exporter can paper over, and it is not a problem in practice because nothing in this adapter
 * (or the {@code OntologyPort} contract) ever compares two independently-produced export byte
 * strings — only the *mapped* {@link org.sequeless.spi.meta.MetaModelSnapshot}, which never
 * observes blank node identity, is compared for equality anywhere.
 *
 * <p>Package-private: only {@link JenaOntologyPort} calls this.
 */
final class TurtleExporter {

    private TurtleExporter() {}

    /**
     * @param model a built, non-{@code null} {@link OntModel}; may be inconsistent — this method
     *     performs no validation of its own
     * @return a non-{@code null} {@link OntologyDocument} holding {@code model}'s base graph as
     *     sorted Turtle, carrying {@code model}'s declared namespace prefixes
     */
    static OntologyDocument export(OntModel model) {
        Model base = model.getBaseModel();

        List<Statement> sorted = new ArrayList<>(base.listStatements().toList());
        sorted.sort(Comparator.comparing(Statement::toString));

        Model sortedForWrite = ModelFactory.createDefaultModel();
        sortedForWrite.setNsPrefixes(base.getNsPrefixMap());
        sortedForWrite.add(sorted);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RDFWriter.create().source(sortedForWrite).format(RDFFormat.TURTLE_FLAT).build().output(out);

        return new OntologyDocument(out.toString(StandardCharsets.UTF_8), OntologyFormat.TURTLE);
    }
}
