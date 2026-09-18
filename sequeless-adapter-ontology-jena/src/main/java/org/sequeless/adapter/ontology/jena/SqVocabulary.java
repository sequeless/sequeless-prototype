package org.sequeless.adapter.ontology.jena;

import java.util.List;
import java.util.Optional;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The {@code sq:} annotation vocabulary (namespace {@code https://sequeless.dev/ns/meta#}) as Jena
 * {@link Property} constants, plus rejection of the terms reserved for later phases. This class is
 * a direct transcription of {@code docs/architecture/sq-vocabulary.md} — that document is the
 * source of truth; this class must not drift from it, in particular the constant used for each
 * term's local name and the exact wording of {@link #rejectionMessageIfReserved}'s message.
 *
 * <p>Package-private: only {@link SnapshotMapper} (annotation lookups) and {@link
 * OntModelBuilder}-adjacent validation need these terms; nothing outside this adapter should ever
 * construct a {@code sq:} IRI by hand.
 */
final class SqVocabulary {

    /** The {@code sq:} namespace, including the trailing {@code #} fragment separator. */
    static final String NS = "https://sequeless.dev/ns/meta#";

    /** {@code sq:label} — overrides {@code rdfs:label}; see {@link SnapshotMapper}'s label resolution. */
    static final Property LABEL = property("label");

    /** {@code sq:displayOrder} — feeds {@code DisplayHints.order()}. */
    static final Property DISPLAY_ORDER = property("displayOrder");

    /** {@code sq:displayGroup} — feeds {@code DisplayHints.group()}. */
    static final Property DISPLAY_GROUP = property("displayGroup");

    /** {@code sq:hidden} — feeds {@code DisplayHints.hidden()}. */
    static final Property HIDDEN = property("hidden");

    /** {@code sq:facet} — feeds {@code PropertyDefinition.facet()}. */
    static final Property FACET = property("facet");

    /** {@code sq:indexed} — feeds {@code PropertyDefinition.indexed()}. */
    static final Property INDEXED = property("indexed");

    /** {@code sq:searchable} — feeds {@code PropertyDefinition.searchable()}. */
    static final Property SEARCHABLE = property("searchable");

    /** {@code sq:readOnly} — feeds {@code PropertyDefinition.readOnly()}. */
    static final Property READ_ONLY = property("readOnly");

    /** {@code sq:displayLabel} — feeds {@code PropertyDefinition.displayLabel()}. */
    static final Property DISPLAY_LABEL = property("displayLabel");

    /** {@code sq:abstract} — feeds {@code TypeDefinition.isAbstract()}. */
    static final Property ABSTRACT = property("abstract");

    /**
     * Every {@code sq:} term implemented by a later phase, in the order they appear in the
     * vocabulary document, together with the phase/topic that message names. Checked in this fixed
     * order so that an ontology using more than one reserved term still fails with a deterministic
     * message rather than one that depends on {@code Map} iteration order.
     */
    private static final List<ReservedTerm> RESERVED_TERMS = List.of(
        new ReservedTerm("StateMachine", "Phase 5 (state machines and automation)"),
        new ReservedTerm("permission", "Phase 8 (authorisation)"),
        new ReservedTerm("materialised", "Phase 4 (derived properties)"));

    private SqVocabulary() {}

    /**
     * Checks {@code model}'s base graph for any use of a term reserved for a later phase — as a
     * declared subject, as a predicate (used as an annotation), or as an object (e.g. an {@code
     * rdf:type} value) — and if found, produces the rejection message
     * {@code sq-vocabulary.md} commits to: {@code "sq:<term> is reserved for Phase <N> (<topic>)
     * and is not supported yet."}
     *
     * <p>Checks the base model, not the reasoner-expanded union: reasoning over an already-loaded
     * model can only relate resources that already exist in it, so it can never manufacture a use
     * of a term the source document never mentioned, and checking the base model keeps this
     * independent of {@link ReasonerSetting}.
     *
     * @param model the model to check; must not be {@code null}
     * @return the rejection message for the first reserved term found (in the fixed order above),
     *     or {@link Optional#empty()} if the document uses none of them
     */
    static Optional<String> rejectionMessageIfReserved(OntModel model) {
        for (ReservedTerm term : RESERVED_TERMS) {
            var resource = ResourceFactory.createResource(NS + term.localName());
            if (model.getBaseModel().containsResource(resource)) {
                return Optional.of(
                    "sq:" + term.localName() + " is reserved for " + term.reservedFor()
                        + " and is not supported yet.");
            }
        }
        return Optional.empty();
    }

    private static Property property(String localName) {
        return ResourceFactory.createProperty(NS, localName);
    }

    /**
     * One reserved {@code sq:} term and the phase/topic {@link #rejectionMessageIfReserved} names
     * for it, matching the "Reserved terms" table in {@code sq-vocabulary.md}.
     */
    private record ReservedTerm(String localName, String reservedFor) {}
}
