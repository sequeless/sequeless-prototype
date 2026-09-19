package org.sequeless.adapter.ontology.jena;

import java.util.List;
import java.util.Optional;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
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

    /** {@code sq:derivedBy} — feeds {@code PropertyDefinition.derivation()}. */
    static final Property DERIVED_BY = property("derivedBy");

    /** {@code sq:function} — feeds {@code RollupRule.function()}. */
    static final Property FUNCTION = property("function");

    /** {@code sq:over} — feeds {@code RollupRule.sourceTypeIri()}. */
    static final Property OVER = property("over");

    /** {@code sq:via} — feeds {@code RollupRule.viaIri()}. */
    static final Property VIA = property("via");

    /** {@code sq:of} — feeds {@code RollupRule.ofPropertyIri()}. */
    static final Property OF = property("of");

    /** {@code sq:filter} — feeds {@code RollupRule.criteria()}. */
    static final Property FILTER = property("filter");

    /** {@code sq:property} — feeds {@code Criterion.property()}. */
    static final Property PROPERTY = property("property");

    /** {@code sq:operator} — feeds {@code Criterion.operator()}. */
    static final Property OPERATOR = property("operator");

    /** {@code sq:value} — feeds {@code Criterion.value()}. */
    static final Property VALUE = property("value");

    /** {@code sq:pluginName} — feeds {@code PluginRule.pluginName()}. */
    static final Property PLUGIN_NAME = property("pluginName");

    /** {@code sq:appliesTo} — feeds the type key of {@code TypeDefinition.stateMachine()}. */
    static final Property APPLIES_TO = property("appliesTo");

    /** {@code sq:initialState} — feeds {@code StateMachineDefinition.initialState()}. */
    static final Property INITIAL_STATE = property("initialState");

    /** {@code sq:state} — feeds {@code StateMachineDefinition.states()}; plain multi-valued. */
    static final Property STATE = property("state");

    /** {@code sq:transition} — feeds {@code StateMachineDefinition.transitions()}; plain multi-valued. */
    static final Property TRANSITION = property("transition");

    /** {@code sq:name} — feeds {@code Transition.name()}. */
    static final Property NAME = property("name");

    /** {@code sq:from} — feeds {@code Transition.fromStateIri()}. */
    static final Property FROM = property("from");

    /** {@code sq:to} — feeds {@code Transition.toStateIri()}. */
    static final Property TO = property("to");

    /** {@code sq:trigger} — validated only; this phase's one legal value is {@link #USER_ACTION}. */
    static final Property TRIGGER = property("trigger");

    /** {@code sq:guard} — feeds {@code Transition.guard()}. */
    static final Property GUARD = property("guard");

    /** {@code sq:guardMessage} — feeds {@code Transition.guardMessage()}. */
    static final Property GUARD_MESSAGE = property("guardMessage");

    /** {@code sq:action} — feeds {@code Transition.actions()}; an {@code rdf:List}. */
    static final Property ACTION = property("action");

    /**
     * {@code sq:expression} — feeds {@code SetPropertyAction.expression()} /
     * {@code PropertyAssignment.expression()}.
     */
    static final Property EXPRESSION = property("expression");

    /** {@code sq:type} — feeds {@code CreateObjectAction.typeIri()}. */
    static final Property TYPE = property("type");

    /** {@code sq:properties} — feeds {@code CreateObjectAction.properties()}; an {@code rdf:List}. */
    static final Property PROPERTIES = property("properties");

    /** {@code sq:url} — feeds {@code WebhookAction.url()}. */
    static final Property URL = property("url");

    /** {@code sq:method} — feeds {@code WebhookAction.method()}; defaults to {@code "POST"}. */
    static final Property METHOD = property("method");

    /** {@code sq:body} — feeds {@code WebhookAction.body()}. */
    static final Property BODY = property("body");

    /** {@code sq:message} — feeds {@code LogAction.message()}. */
    static final Property MESSAGE = property("message");

    /**
     * {@code sq:Rollup} — types a {@code sq:derivedBy} blank node as a declarative aggregate. A
     * node typed both this and {@link #PLUGIN}, or neither, is a rule-shape {@code ERROR}.
     */
    static final Resource ROLLUP = ResourceFactory.createResource(NS + "Rollup");

    /**
     * {@code sq:Plugin} — types a {@code sq:derivedBy} blank node as a code-backed derivation
     * looked up by {@code sq:pluginName}. A node typed both this and {@link #ROLLUP}, or neither,
     * is a rule-shape {@code ERROR}.
     */
    static final Resource PLUGIN = ResourceFactory.createResource(NS + "Plugin");

    /** {@code sq:Criterion} — types each element of a {@code sq:filter} RDF list. */
    static final Resource CRITERION = ResourceFactory.createResource(NS + "Criterion");

    /** {@code sq:StateMachine} — types a node attached to a type via {@link #APPLIES_TO}. */
    static final Resource STATE_MACHINE = ResourceFactory.createResource(NS + "StateMachine");

    /** {@code sq:State} — types each element of a {@link #STATE} triple. */
    static final Resource STATE_CLASS = ResourceFactory.createResource(NS + "State");

    /** {@code sq:Transition} — types each element of a {@link #TRANSITION} triple. */
    static final Resource TRANSITION_CLASS = ResourceFactory.createResource(NS + "Transition");

    /**
     * {@code sq:SetProperty} — one of the four {@code sq:action} list element kinds. A node typed
     * as none or more than one of {@link #SET_PROPERTY}/{@link #CREATE_OBJECT}/{@link #WEBHOOK}/
     * {@link #LOG} is a rule-shape {@code ERROR}, mirroring {@link #ROLLUP}/{@link #PLUGIN}.
     */
    static final Resource SET_PROPERTY = ResourceFactory.createResource(NS + "SetProperty");

    /** {@code sq:CreateObject} — one of the four {@code sq:action} list element kinds. */
    static final Resource CREATE_OBJECT = ResourceFactory.createResource(NS + "CreateObject");

    /** {@code sq:PropertyAssignment} — types each element of a {@link #PROPERTIES} RDF list. */
    static final Resource PROPERTY_ASSIGNMENT = ResourceFactory.createResource(NS + "PropertyAssignment");

    /** {@code sq:Webhook} — one of the four {@code sq:action} list element kinds. */
    static final Resource WEBHOOK = ResourceFactory.createResource(NS + "Webhook");

    /** {@code sq:Log} — one of the four {@code sq:action} list element kinds. */
    static final Resource LOG = ResourceFactory.createResource(NS + "Log");

    /** {@code sq:UserAction} — the only legal {@link #TRIGGER} value this phase supports. */
    static final Resource USER_ACTION = ResourceFactory.createResource(NS + "UserAction");

    /**
     * Every {@code sq:} term implemented by a later phase, in the order they appear in the
     * vocabulary document, together with the phase/topic that message names. Checked in this fixed
     * order so that an ontology using more than one reserved term still fails with a deterministic
     * message rather than one that depends on {@code Map} iteration order.
     */
    private static final List<ReservedTerm> RESERVED_TERMS = List.of(
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
