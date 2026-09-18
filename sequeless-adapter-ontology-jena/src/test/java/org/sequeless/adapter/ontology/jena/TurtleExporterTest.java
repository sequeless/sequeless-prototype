package org.sequeless.adapter.ontology.jena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.testkit.Fixtures;

/**
 * Verifies the two things the T8 handback's probe established empirically: exporting {@link
 * OntModel#getBaseModel()} produces Turtle that (a) re-parses back into an isomorphic base graph —
 * proving the exporter really does use the base model, not the much larger reasoner-expanded union
 * — and (b) maps, via {@link SnapshotMapper}, to a structurally identical set of types and
 * properties as the original, which is the guarantee {@code OntologyContract}'s
 * {@code exportThenImportRoundTrips} test actually depends on. Byte-for-byte identity between two
 * *independently built* exports of the same source text is deliberately not asserted here: Jena
 * assigns fresh internal blank-node ids per parse, so two independent builds legitimately produce
 * different (but isomorphic) Turtle -- see {@link TurtleExporter}'s javadoc.
 */
class TurtleExporterTest {

    @Test
    void exportedTurtleReimportsToAnIsomorphicBaseModel() {
        OntModel original = buildModel(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);

        OntologyDocument exported = TurtleExporter.export(original);
        assertThat(exported.format()).isEqualTo(OntologyFormat.TURTLE);
        assertThat(exported.content()).isNotBlank();

        OntModel reimported = buildModel(exported.content(), ReasonerSetting.OWL);

        assertThat(reimported.getBaseModel().isIsomorphicWith(original.getBaseModel())).isTrue();
    }

    @Test
    void exportUsesTheBaseModelNotTheReasonerExpandedUnion() {
        OntModel original = buildModel(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);

        OntologyDocument exported = TurtleExporter.export(original);
        OntModel reimported = buildModel(exported.content(), ReasonerSetting.OWL);

        // The union view carries hundreds of inferred triples (see plan.md's verified 56-vs-325
        // figure); the base model this fixture actually asserts is two orders of magnitude smaller.
        // Re-importing an export of the *union* would have produced a base model at least as large
        // as the original union, not smaller than it.
        assertThat(reimported.getBaseModel().size()).isLessThan(original.size());
        assertThat(reimported.getBaseModel().size()).isEqualTo(original.getBaseModel().size());
    }

    @Test
    void exportedTurtleMapsToTheSameTypesAsTheOriginal() {
        OntModel original = buildModel(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);
        OntologyDocument exported = TurtleExporter.export(original);
        OntModel reimported = buildModel(exported.content(), ReasonerSetting.OWL);

        MappingResult originalMapping = SnapshotMapper.map(original);
        MappingResult reimportedMapping = SnapshotMapper.map(reimported);

        assertThat(reimportedMapping.types()).isEqualTo(originalMapping.types());
        assertThat(reimportedMapping.issues()).isEqualTo(originalMapping.issues());
    }

    @Test
    void callingExportTwiceOnTheSameModelIsByteIdentical() {
        OntModel model = buildModel(Fixtures.referenceOntology().content(), ReasonerSetting.OWL);

        OntologyDocument first = TurtleExporter.export(model);
        OntologyDocument second = TurtleExporter.export(model);

        assertThat(second.content()).isEqualTo(first.content());
    }

    private static OntModel buildModel(String turtle, ReasonerSetting reasoner) {
        Model rdfModel = ModelFactory.createDefaultModel();
        RDFDataMgr.read(
            rdfModel,
            new ByteArrayInputStream(turtle.getBytes(StandardCharsets.UTF_8)),
            "urn:sequeless:test",
            Lang.TURTLE);
        return OntModelFactory.createModel(
            rdfModel.getGraph(), reasoner.specification(), ImportResolver.withBundledMeta());
    }
}
