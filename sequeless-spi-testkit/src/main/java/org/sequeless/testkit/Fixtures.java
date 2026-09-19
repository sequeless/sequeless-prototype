package org.sequeless.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyFormat;

/**
 * Shared, port-agnostic test data for every contract in this testkit. This class lives at the top
 * level of {@code org.sequeless.testkit} — not under a per-port subpackage such as {@code
 * org.sequeless.testkit.authz} — precisely because later contracts (an {@code ObjectStoreContract},
 * a {@code QueryContract}) need the same tenant, principal, and scope building blocks that {@link
 * org.sequeless.testkit.authz.AuthorizationContract} needs, and none of that data is specific to
 * authorization.
 */
public final class Fixtures {

    /**
     * An opaque resource identifier with no special meaning to any port — distinct from {@link
     * org.sequeless.spi.authz.AuthorizationPort#EVERYTHING}, so a contract can assert that
     * implementations accept arbitrary resource strings and not just the sentinel.
     */
    public static final String OPAQUE_RESOURCE = "sq:SampleType/1";

    /** Document IRI of the reference-domain ontology — note: no trailing {@code #}. */
    public static final String REFERENCE_ONTOLOGY_IRI = "https://sequeless.dev/ns/ref";

    /** Document IRI of the deliberately inconsistent variant. */
    public static final String INCONSISTENT_ONTOLOGY_IRI = "https://sequeless.dev/ns/ref-inconsistent";

    /** Document IRI of the variant exercising inverse and transitive object properties. */
    public static final String INVERSE_TRANSITIVE_ONTOLOGY_IRI =
        "https://sequeless.dev/ns/ref-inverse-transitive";

    /**
     * Term namespace of the reference domain. Every {@code REFERENCE_*} IRI constant below is this
     * namespace plus a local name; contracts and adapter tests must compare against these constants
     * rather than rebuild IRIs by hand, so a namespace change stays a one-line edit here.
     */
    private static final String REF = REFERENCE_ONTOLOGY_IRI + "#";

    /** {@code ex:Deliverable} — abstract root of the reference hierarchy. */
    public static final String DELIVERABLE_IRI = REF + "Deliverable";

    /** {@code ex:WorkItem} — abstract, subclass of {@link #DELIVERABLE_IRI}. */
    public static final String WORK_ITEM_IRI = REF + "WorkItem";

    /** {@code ex:Task} — concrete, subclass of {@link #WORK_ITEM_IRI}. */
    public static final String TASK_IRI = REF + "Task";

    /** {@code ex:Project} — concrete, subclass of {@link #WORK_ITEM_IRI}. */
    public static final String PROJECT_IRI = REF + "Project";

    /** {@code ex:Person} — concrete, standing outside the work-item hierarchy. */
    public static final String PERSON_IRI = REF + "Person";

    /** {@code ex:title} — {@code xsd:string}, searchable, declared on {@link #DELIVERABLE_IRI}. */
    public static final String TITLE_IRI = REF + "title";

    /** {@code ex:status} — {@code xsd:string}, facet and indexed, declared on {@link #WORK_ITEM_IRI}. */
    public static final String STATUS_IRI = REF + "status";

    /** {@code ex:priority} — {@code xsd:integer}, declared on {@link #TASK_IRI}. */
    public static final String PRIORITY_IRI = REF + "priority";

    /** {@code ex:createdAt} — {@code xsd:dateTime}, hidden and read-only. */
    public static final String CREATED_AT_IRI = REF + "createdAt";

    /** {@code ex:assignedTo} — object property to {@link #PERSON_IRI}, facet, max cardinality 1. */
    public static final String ASSIGNED_TO_IRI = REF + "assignedTo";

    /**
     * {@code ex:belongsToProject} — functional object property to {@link #PROJECT_IRI}. This is the
     * only side on which {@code owl:inverseOf} is asserted, which is precisely what makes {@link
     * #HAS_TASK_IRI}'s inverse an <em>inferred</em> fact rather than a stated one.
     */
    public static final String BELONGS_TO_PROJECT_IRI = REF + "belongsToProject";

    /**
     * {@code ex:hasTask} — object property to {@link #TASK_IRI} carrying no inverse assertion of its
     * own; its inverse is visible only when the OWL reasoner is active.
     */
    public static final String HAS_TASK_IRI = REF + "hasTask";

    /** {@code ex:estimatedHours} — {@code xsd:decimal}, declared on {@link #TASK_IRI}. */
    public static final String ESTIMATED_HOURS_IRI = REF + "estimatedHours";

    /** {@code ex:dueDate} — {@code xsd:date}, declared on {@link #TASK_IRI}. */
    public static final String DUE_DATE_IRI = REF + "dueDate";

    /** {@code ex:description} — {@code xsd:string}, searchable, declared on {@link #WORK_ITEM_IRI}. */
    public static final String DESCRIPTION_IRI = REF + "description";

    /** {@code ex:name} — {@code xsd:string}, declared on {@link #PERSON_IRI}, min cardinality 1. */
    public static final String NAME_IRI = REF + "name";

    /** {@code ex:email} — {@code xsd:string}, declared on {@link #PERSON_IRI}. */
    public static final String EMAIL_IRI = REF + "email";

    /**
     * {@code ex:openTaskCount} — {@code xsd:integer}, declared on {@link #PROJECT_IRI}; {@code
     * sq:Rollup}-derived ({@code count} of {@link #TASK_IRI} via {@link #BELONGS_TO_PROJECT_IRI},
     * filtered to {@code status != "done"}).
     */
    public static final String OPEN_TASK_COUNT_IRI = REF + "openTaskCount";

    /**
     * {@code ex:totalEstimatedHours} — {@code xsd:decimal}, declared on {@link #PROJECT_IRI};
     * {@code sq:Rollup}-derived ({@code sum} of {@link #ESTIMATED_HOURS_IRI} over {@link
     * #TASK_IRI} via {@link #BELONGS_TO_PROJECT_IRI}, no filter).
     */
    public static final String TOTAL_ESTIMATED_HOURS_IRI = REF + "totalEstimatedHours";

    /**
     * {@code ex:workload} — {@code xsd:decimal}, declared on {@link #PERSON_IRI} only in {@link
     * #referencePluginOntology()} and {@link #unknownPluginOntology()} (not in {@link
     * #referenceOntology()}); {@code sq:Plugin}-derived, dispatching by {@code sq:pluginName} to a
     * registered {@code DerivationPlugin}.
     */
    public static final String WORKLOAD_IRI = REF + "workload";

    private static final String REFERENCE_TURTLE = readClasspathResource("/ontology/reference.ttl");
    private static final String INCONSISTENT_TURTLE =
        readClasspathResource("/ontology/inconsistent.ttl");
    private static final String INVERSE_TRANSITIVE_TURTLE =
        readClasspathResource("/ontology/inverse-transitive.ttl");
    private static final String REFERENCE_PLUGIN_TURTLE =
        readClasspathResource("/ontology/reference-plugin.ttl");
    private static final String UNKNOWN_PLUGIN_TURTLE =
        readClasspathResource("/ontology/unknown-plugin.ttl");

    private Fixtures() {}

    /**
     * @return a {@link Scope} for the default tenant and the anonymous principal, suitable
     *     wherever a test needs a valid scope but does not care about tenant or identity
     */
    public static Scope defaultScope() {
        return new Scope(TenantId.DEFAULT, Principal.ANONYMOUS);
    }

    /**
     * @param principalId the id (and display name) to give the principal; must not be blank
     * @param roles the roles to assign the principal
     * @return a {@link Scope} for the default tenant and a principal identified by {@code
     *     principalId} carrying {@code roles}
     */
    public static Scope scope(String principalId, String... roles) {
        return new Scope(TenantId.DEFAULT, new Principal(principalId, principalId, Set.of(roles)));
    }

    /**
     * The reference-domain ontology: a three-level class hierarchy ({@code Deliverable} ⊐ {@code
     * WorkItem} ⊐ {@code Task}, {@code Project}) plus {@code Person}, annotated with the full {@code
     * sq:} vocabulary and importing it by IRI.
     *
     * <p>The hierarchy is three levels deep on purpose. With only two, an ontology port that does no
     * reasoning at all still reports {@code Task}'s asserted superclass, so a test asserting "{@code
     * WorkItem} is a supertype of {@code Task}" would pass whether or not inference ran, and would
     * prove nothing about the reasoner. The third level is what makes the difference observable.
     *
     * @return a fresh {@link OntologyDocument} holding the reference ontology as Turtle
     */
    public static OntologyDocument referenceOntology() {
        return new OntologyDocument(REFERENCE_TURTLE, OntologyFormat.TURTLE);
    }

    /**
     * A deliberately inconsistent ontology: {@code Cyborg} is declared a subclass of two classes
     * stated to be {@code owl:disjointWith} one another, and an individual is asserted into it. Any
     * conforming port must reject this rather than return a degraded snapshot.
     *
     * @return a fresh {@link OntologyDocument} holding the inconsistent ontology as Turtle
     */
    public static OntologyDocument inconsistentOntology() {
        return new OntologyDocument(INCONSISTENT_TURTLE, OntologyFormat.TURTLE);
    }

    /**
     * A variant exercising {@code owl:TransitiveProperty} and an {@code owl:inverseOf} pair asserted
     * from the opposite side to {@link #referenceOntology()}'s, so an adapter cannot pass by
     * hardcoding the direction the reference ontology happens to use.
     *
     * @return a fresh {@link OntologyDocument} holding the inverse/transitive ontology as Turtle
     */
    public static OntologyDocument inverseTransitiveOntology() {
        return new OntologyDocument(INVERSE_TRANSITIVE_TURTLE, OntologyFormat.TURTLE);
    }

    /**
     * {@link #referenceOntology()} plus one {@code sq:Plugin}-derived property, {@link
     * #WORKLOAD_IRI} on {@link #PERSON_IRI}, whose {@code sq:pluginName} ({@code "workload"})
     * matches the sample {@code WorkloadDerivationPlugin} this module registers under {@code
     * META-INF/services/org.sequeless.spi.derivation.DerivationPlugin}. This is the fixture a
     * {@code @SpringBootTest} points {@code sequeless.ontology.source} at to prove a plug-in is
     * discovered and executed end to end.
     *
     * @return a fresh {@link OntologyDocument} holding the reference-plus-plugin ontology as Turtle
     */
    public static OntologyDocument referencePluginOntology() {
        return new OntologyDocument(REFERENCE_PLUGIN_TURTLE, OntologyFormat.TURTLE);
    }

    /**
     * Identical in shape to {@link #referencePluginOntology()}, except {@link #WORKLOAD_IRI}'s
     * {@code sq:pluginName} names a plug-in nothing on the classpath registers. Activation must
     * fail with an ERROR {@link org.sequeless.spi.ontology.OntologyIssue} naming {@link
     * #WORKLOAD_IRI}, exactly as an unresolved import or a reserved term does.
     *
     * @return a fresh {@link OntologyDocument} holding the unknown-plugin ontology as Turtle
     */
    public static OntologyDocument unknownPluginOntology() {
        return new OntologyDocument(UNKNOWN_PLUGIN_TURTLE, OntologyFormat.TURTLE);
    }

    /**
     * Reads a bundled Turtle fixture from the classpath, not the filesystem: this testkit reaches
     * adapter modules as a jar, so any {@code File}- or {@code Path}-relative read would work in this
     * module's own tests and then fail everywhere it actually matters.
     *
     * @param resource an absolute classpath resource path, e.g. {@code /ontology/reference.ttl}
     * @return the resource's full contents, decoded as UTF-8
     * @throws IllegalStateException if no such resource is on the classpath — a packaging mistake
     *     deserves a message naming the missing path, not a {@link NullPointerException} thrown
     *     later from somewhere unrelated
     * @throws UncheckedIOException if the resource exists but cannot be read
     */
    private static String readClasspathResource(String resource) {
        try (InputStream stream = Fixtures.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(
                    "Missing testkit ontology fixture on the classpath: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read testkit ontology fixture: " + resource, e);
        }
    }
}
