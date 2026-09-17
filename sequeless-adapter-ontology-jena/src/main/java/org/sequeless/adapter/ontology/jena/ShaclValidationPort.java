package org.sequeless.adapter.ontology.jena;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.jena.graph.Graph;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.validation.ReportEntry;
import org.apache.jena.sparql.path.P_Link;
import org.apache.jena.sparql.path.Path;
import org.sequeless.spi.Scope;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.validation.ValidationPort;
import org.sequeless.spi.validation.Violation;

/**
 * The default {@link ValidationPort} adapter, backed by {@code org.apache.jena.shacl} (jena-shacl,
 * verified against the resolved {@code jena-shacl-6.1.0.jar}, not merely assumed from older Jena
 * API shapes — see this class's constructor javadoc and plan §4 "Validation split").
 *
 * <h2>Why this class depends on the concrete {@link JenaOntologyPort}, not {@link
 * org.sequeless.spi.ontology.OntologyPort}</h2>
 *
 * <p>SHACL shapes live in the same ontology document the running {@link
 * org.sequeless.spi.ontology.OntologyPort} serves, and must always reflect whatever that port's
 * current ontology is — including after a live {@code importDocument} swaps it. {@link
 * JenaOntologyPort#currentModel(Scope)} is the package-private accessor that exposes the live
 * {@code OntModel} (and, via {@link org.apache.jena.rdf.model.Model#getGraph()}, the shapes graph
 * embedded in it) to code in this same package. The SPI's {@code OntologyPort} interface
 * deliberately has no such accessor — see the ArchUnit rule that nothing outside this adapter may
 * see Jena types — so this class must be constructed with the concrete {@link JenaOntologyPort}
 * rather than the interface. {@link ShaclValidationAutoConfiguration} is what guarantees the same
 * {@link JenaOntologyPort} instance backs both the {@code OntologyPort} and this {@code
 * ValidationPort} bean.
 */
public final class ShaclValidationPort implements ValidationPort {

    private final JenaOntologyPort ontologyPort;

    /**
     * @param ontologyPort the Jena adapter whose live ontology this port reads {@code sh:} shapes
     *     from; must not be {@code null}
     * @throws NullPointerException if {@code ontologyPort} is {@code null}
     */
    public ShaclValidationPort(JenaOntologyPort ontologyPort) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
    }

    @Override
    public List<Violation> validate(Scope scope, MetaModelSnapshot snapshot, BusinessObject object) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        Objects.requireNonNull(object, "object must not be null");

        Graph shapesGraph = ontologyPort.currentModel(scope).getGraph();
        Shapes shapes = Shapes.parse(shapesGraph);
        Graph dataGraph = ShaclDataGraphBuilder.build(snapshot, object);

        ValidationReport report = ShaclValidator.get().validate(shapes, dataGraph);

        List<Violation> violations = new ArrayList<>();
        for (ReportEntry entry : report.getEntries()) {
            violations.add(new Violation(pathIriOf(entry), messageOf(entry)));
        }
        return List.copyOf(violations);
    }

    /**
     * @param entry a single non-conforming {@link ReportEntry}
     * @return the IRI of {@link ReportEntry#resultPath()}'s single {@code sh:path} predicate, or
     *     {@code ""} when the entry has no path (a node-level constraint) — {@code resultPath()}'s
     *     declared return type is {@link Path}, confirmed against the resolved jar; every {@code
     *     sh:path} in this project's shapes is a plain predicate IRI, which jena-shacl represents as
     *     a {@link P_Link}, so that is the only case unwrapped here. A compound path (sequence,
     *     inverse, alternative) is not produced by any shape this project declares, so it falls back
     *     to {@link Path#toString()} rather than being unwrapped further.
     */
    private static String pathIriOf(ReportEntry entry) {
        Path path = entry.resultPath();
        if (path == null) {
            return "";
        }
        if (path instanceof P_Link link) {
            return link.getNode().getURI();
        }
        return path.toString();
    }

    private static String messageOf(ReportEntry entry) {
        String message = entry.message();
        return (message == null || message.isBlank()) ? "SHACL constraint violated" : message;
    }
}
