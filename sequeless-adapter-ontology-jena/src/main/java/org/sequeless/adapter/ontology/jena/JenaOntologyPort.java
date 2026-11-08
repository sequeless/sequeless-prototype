package org.sequeless.adapter.ontology.jena;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.jena.ontapi.model.OntModel;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.spi.derivation.DerivationPlugin;
import org.sequeless.spi.meta.automation.Automation;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.behaviour.Behaviour;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.behaviour.DerivationRule;
import org.sequeless.spi.meta.behaviour.PluginRule;
import org.sequeless.spi.meta.schema.OntologyVersion;
import org.sequeless.spi.meta.schema.PropertyDefinition;
import org.sequeless.spi.meta.schema.Schema;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.meta.schema.TypeDefinition;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyAdministration;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;

/**
 * The default adapter for the ontology ports, backed by Apache Jena's {@code ontapi}. It implements
 * the three read ports, one per half of an activation ({@link SchemaSource}, {@link
 * BehaviourSource} and {@link AutomationSource}), and {@link OntologyAdministration}. Two static
 * factories mirror the two ways an adapter can be pointed at an ontology: {@link
 * #fromDocument(OntologyDocument, ReasonerSetting)} for an in-memory document (tests, the contract
 * testkit), {@link #fromSource(String, String, ReasonerSetting)} for a configured {@code
 * classpath:}/{@code file:} location (the running application, via {@code
 * JenaOntologyAutoConfiguration} in a later task).
 *
 * <h2>Build fresh, validate, swap only on success</h2>
 *
 * <p>Every method that can change what this port serves — the constructor, {@link
 * #reload(Scope)}, {@link #importDocument(Scope, OntologyDocument, ImportMode)} — funnels through
 * {@link #buildState(Supplier)}, which does the entire parse → resolve-imports → consistency-check
 * → map pipeline against a *new*, private {@link Built} value, touching nothing this port has
 * previously published. Only once that fresh build exists in hand does the caller decide what to
 * do with it:
 *
 * <ul>
 *   <li>The constructor always adopts it, consistent or not — see the {@code fromDocument} F20
 *       note below.
 *   <li>{@link #reload(Scope)} and {@link #importDocument(Scope, OntologyDocument, ImportMode)}
 *       adopt it (assign it to {@link #current}) only if it turned out consistent; otherwise they
 *       throw {@link OntologyException} carrying the *fresh* report and leave {@link #current}
 *       untouched.
 * </ul>
 *
 * <p>This is why {@link #current} is a single {@code volatile} field holding one {@link PortState}
 * record — never separate fields for "the schema" and "the behaviour". If those lived in separate
 * fields, a thread reading one half could observe one field already swapped to the new build and
 * the other still holding the old one — a torn pair that belongs to neither the old ontology nor
 * the new one. A single reference swap on a {@code volatile} field is atomic with respect to every
 * other thread by the Java Memory Model, so any reader always sees either entirely-old or
 * entirely-new state, never a mixture, and never needs a lock to read it.
 *
 * <h2>Versions</h2>
 *
 * <p>Each build is stamped with an {@link OntologyVersion} only when it is adopted, so all three
 * halves of an activation carry the same version. With a {@link #documentStore} the version is the
 * stored one: the version {@link OntologyDocumentStore#activate} returns on import, or {@code
 * active(scope).version()} at startup and on reload. Without a store, a counter starts at 1 and is
 * incremented by each successful reload or import. A failed reload or import takes no version.
 *
 * <h2>{@code fromDocument} never throws for a bad document (F20)</h2>
 *
 * <p>The constructor calls {@link #buildState(Supplier)} and unconditionally adopts the result,
 * even when the freshly built ontology is inconsistent or could not even be parsed. Every judgement
 * about whether the loaded ontology is *usable* — thrown as {@link OntologyException} — is deferred
 * to the read ports, {@link #export(Scope, OntologyFormat)}, {@link #reload(Scope)} and {@link
 * #importDocument(Scope, OntologyDocument, ImportMode)}, exactly as {@link
 * OntologyAdministration}'s own javadoc requires. This is not merely a style choice: {@code
 * OntologyContract}'s {@code inconsistentOntologyMakesSchemaThrow} test (and its Behaviour and
 * Automation siblings) calls {@code portFor(inconsistentDocument)} completely unguarded and only
 * wraps the subsequent read in an assertion — a constructor that throws for a bad document would
 * already fail that test before the assertion is even reached.
 *
 * <h2>What "fresh" means for {@link #reload(Scope)}</h2>
 *
 * <p>Without a {@link #documentStore} — the {@code fromDocument}/{@code fromSource} construction
 * paths — {@link #reload(Scope)} always re-runs the pipeline against {@link #sourceLoader}, the
 * same classpath/file location or in-memory bytes this port was originally constructed with. A
 * prior successful {@link #importDocument(Scope, OntologyDocument, ImportMode)} changes only {@link
 * #current}, never {@link #sourceLoader}: importing a document is a live, in-memory override, not a
 * change to this adapter's configured backing source, so a subsequent {@link #reload(Scope)}
 * deliberately reverts to whatever the original source currently contains. Nothing in {@code
 * OntologyAdministration}'s contract or {@code OntologyContract} exercises this interaction, but it
 * is worth a future test knowing about explicitly rather than discovering by surprise.
 *
 * <p>With a {@link #documentStore} — the {@link #fromStore} construction path — {@link
 * #reload(Scope)} instead re-reads whatever document is currently active in the store for the
 * given {@code scope}, which may differ from both {@link #sourceLoader} and this port's own
 * in-memory copy if something changed the store out-of-band (another instance's {@link
 * #importDocument}, or a direct write to the store).
 *
 * <h2>Concurrency</h2>
 *
 * <p>{@link #reload(Scope)} and {@link #importDocument(Scope, OntologyDocument, ImportMode)} are
 * {@code synchronized}. Each one activates a store version (when there is a store) and swaps {@link
 * #current} in one step, so two concurrent activations cannot leave the store and the in-memory
 * state in different orders, and the counter cannot hand a lower version to a later swap. Reads do
 * not take the lock. Do not call {@code reload} or {@code importDocument} inside an outer
 * transaction: the store's activation lock would outlive this monitor and the two could deadlock.
 *
 * <h2>Merging the sources of an {@link OntologyReport}</h2>
 *
 * <p>{@link #buildStateFrom(OntModel)} is where this class earns its keep: it merges {@link
 * ConsistencyChecker}'s reasoner-level {@link OntologyReport}, an unresolved-{@code owl:imports}
 * check ({@link ImportResolver} only ever registers its own bundled {@code sq-meta.ttl} by IRI —
 * verified empirically that {@code GraphRepository.createGraphDocumentRepositoryMem()} silently
 * auto-vivifies an *empty* graph for any other import instead of throwing, so this adapter must
 * detect the gap itself by comparing {@code model.getID().imports()} against the one IRI it knows,
 * rather than by catching an exception that never comes), {@link SqVocabulary}'s reserved-term
 * rejection, {@link SnapshotMapper}'s own mapping issues — {@code WARNING} for a degraded-but-usable
 * mapping, {@code ERROR} for a malformed {@code sq:derivedBy} rule — and {@link
 * #unknownPluginIssues(List, Map)}'s {@code ServiceLoader}-backed check that every {@code sq:Plugin}
 * rule's {@code sq:pluginName} actually resolves to a registered {@link
 * org.sequeless.spi.derivation.DerivationPlugin}, into the single {@link OntologyReport} that ends
 * up inside the {@link PortState} this port serves.
 */
public final class JenaOntologyPort implements SchemaSource, BehaviourSource, AutomationSource, OntologyAdministration {

    /**
     * Used only when the ontology could not be built or mapped at all (a parse failure, or an
     * exception from the schema's own construction, e.g. a short-name collision) — {@link
     * Schema#ontologyIri()} must not be blank, and in these failure cases there is no real ontology
     * IRI to report.
     */
    private static final String UNBUILDABLE_ONTOLOGY_IRI = "urn:sequeless:jena-adapter:unbuildable-ontology";

    /** The version of the first activation, and the value the counter starts from. */
    private static final OntologyVersion FIRST_VERSION = new OntologyVersion(1);

    private final Supplier<InputStream> sourceLoader;
    private final ReasonerSetting reasoner;
    private final Optional<OntologyDocumentStore> documentStore;
    /**
     * The version of the state currently adopted, used as the counter when there is no store. Only
     * {@link #adopt} advances it, and only after a build has been checked, so a failed reload or
     * import takes no number.
     */
    private final AtomicInteger versions;

    private volatile PortState current;

    private JenaOntologyPort(
            Supplier<InputStream> sourceLoader,
            ReasonerSetting reasoner,
            Optional<OntologyDocumentStore> documentStore,
            OntologyVersion initialVersion) {
        this.sourceLoader = sourceLoader;
        this.reasoner = reasoner;
        this.documentStore = documentStore;
        this.versions = new AtomicInteger(initialVersion.value());
        this.current = buildState(sourceLoader).stamp(initialVersion);
    }

    /**
     * @param document the ontology to load; must not be {@code null}
     * @param reasoner which reasoning level to build the model under; must not be {@code null}
     * @return a new port whose current state reflects {@code document}, built eagerly but never
     *     thrown for even a badly broken {@code document} — see this class's F20 note
     * @throws NullPointerException if either argument is {@code null}
     */
    public static JenaOntologyPort fromDocument(OntologyDocument document, ReasonerSetting reasoner) {
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(reasoner, "reasoner must not be null");
        return new JenaOntologyPort(streamOf(document), reasoner, Optional.empty(), FIRST_VERSION);
    }

    /**
     * @param propertyName the configuration property {@code location} came from, used only to
     *     produce a clear error message if it cannot be resolved; must not be {@code null}
     * @param location the {@code classpath:}/{@code file:} location to load from; must not be
     *     {@code null}
     * @param reasoner which reasoning level to build the model under; must not be {@code null}
     * @return a new port whose current state reflects the ontology at {@code location}
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code location} cannot be resolved to a readable
     *     resource — a misconfigured {@code sequeless.ontology.source} is a startup-time
     *     configuration error, deliberately not deferred the way an inconsistent ontology is
     */
    public static JenaOntologyPort fromSource(String propertyName, String location, ReasonerSetting reasoner) {
        Objects.requireNonNull(propertyName, "propertyName must not be null");
        Objects.requireNonNull(location, "location must not be null");
        Objects.requireNonNull(reasoner, "reasoner must not be null");
        return new JenaOntologyPort(
                () -> OntologySource.open(propertyName, location), reasoner, Optional.empty(), FIRST_VERSION);
    }

    /**
     * @param documents the ontology document store to load the active document from, and to activate
     *     newly imported documents into; must not be {@code null}
     * @param scope the tenant to load/seed the active document for at startup; must not be {@code
     *     null}
     * @param sourcePropertyName the configuration property {@code source} came from, used only for
     *     error messages; must not be {@code null}
     * @param source the {@code classpath:}/{@code file:} location to seed from if {@code documents}
     *     has no active document yet for {@code scope.tenantId()}; may be {@code null} or blank only
     *     if {@code documents} already has an active document
     * @param reasoner which reasoning level to build the model under; must not be {@code null}
     * @return a new port backed by {@code documents}: {@link #reload} and {@link #importDocument}
     *     read and write through it from now on, using whatever {@code Scope} each call is made with
     * @throws IllegalStateException if {@code documents} has no active document for {@code
     *     scope.tenantId()} and {@code source} is blank
     * @throws org.sequeless.spi.ontology.OntologyException if a freshly seeded document from {@code
     *     source} is inconsistent — a broken seed is never activated
     */
    public static JenaOntologyPort fromStore(
            OntologyDocumentStore documents,
            Scope scope,
            String sourcePropertyName,
            String source,
            ReasonerSetting reasoner) {
        Objects.requireNonNull(documents, "documents must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(reasoner, "reasoner must not be null");

        Optional<StoredOntologyDocument> active = documents.active(scope);
        StoredOntologyDocument stored;
        if (active.isPresent()) {
            stored = active.get();
        } else {
            if (source == null || source.isBlank()) {
                throw new IllegalStateException("No ontology document stored for tenant '"
                        + scope.tenantId().value() + "' and no '" + sourcePropertyName + "' configured to seed one");
            }
            Objects.requireNonNull(sourcePropertyName, "sourcePropertyName must not be null");
            String turtle;
            try (InputStream in = OntologySource.open(sourcePropertyName, source)) {
                turtle = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read the ontology source to seed the store", e);
            }
            OntologyDocument initial = new OntologyDocument(turtle, OntologyFormat.TURTLE);
            JenaOntologyPort probe = fromDocument(initial, reasoner);
            requireConsistent(probe.current); // throws OntologyException; never activates a broken seed
            stored = documents.activate(scope, initial);
        }

        return new JenaOntologyPort(
                streamOf(stored.document()), reasoner, Optional.of(documents), new OntologyVersion(stored.version()));
    }

    /**
     * @param tenant the tenant whose active ontology is read; must not be {@code null}
     * @return the Schema of the active version
     * @throws NullPointerException if {@code tenant} is {@code null}
     * @throws OntologyException if the active ontology is inconsistent
     */
    @Override
    public Schema schema(TenantId tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return requireConsistent(current).schema();
    }

    /**
     * @param tenant the tenant whose active ontology is read; must not be {@code null}
     * @return the Behaviour of the active version
     * @throws NullPointerException if {@code tenant} is {@code null}
     * @throws OntologyException if the active ontology is inconsistent
     */
    @Override
    public Behaviour behaviour(TenantId tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return requireConsistent(current).behaviour();
    }

    /**
     * @param tenant the tenant whose active ontology is read; must not be {@code null}
     * @return the Automation of the active version
     * @throws NullPointerException if {@code tenant} is {@code null}
     * @throws OntologyException if the active ontology is inconsistent
     */
    @Override
    public Automation automation(TenantId tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return requireConsistent(current).automation();
    }

    @Override
    public synchronized OntologyReport reload(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (documentStore.isPresent()) {
            StoredOntologyDocument active = activeDocument(documentStore.get(), scope);
            return adopt(buildState(streamOf(active.document())), () -> new OntologyVersion(active.version()))
                    .report();
        }
        return adopt(buildState(sourceLoader), () -> new OntologyVersion(versions.incrementAndGet()))
                .report();
    }

    private static StoredOntologyDocument activeDocument(OntologyDocumentStore store, Scope scope) {
        return store.active(scope)
                .orElseThrow(() -> new IllegalStateException("No active ontology document stored for tenant '"
                        + scope.tenantId().value() + "'"));
    }

    @Override
    public OntologyDocument export(Scope scope, OntologyFormat format) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return TurtleExporter.export(requireConsistent(current).model());
    }

    @Override
    public synchronized ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (mode != ImportMode.REPLACE) {
            // ImportMode currently declares only REPLACE; this guards the day it declares MERGE too.
            throw new IllegalArgumentException("Unsupported ImportMode: " + mode);
        }

        PortState adopted = adopt(buildState(streamOf(document)), () -> versionOfImport(scope, document));
        return new ImportReport(true, adopted.report(), adopted.schema().types().size());
    }

    /**
     * The version an imported document takes. With a store, the store assigns it, and {@link #adopt}
     * calls this only after the document is known to be consistent, so a rejected document is never
     * activated in the store.
     */
    private OntologyVersion versionOfImport(Scope scope, OntologyDocument document) {
        return documentStore
                .map(store ->
                        new OntologyVersion(store.activate(scope, document).version()))
                .orElseGet(() -> new OntologyVersion(versions.incrementAndGet()));
    }

    /**
     * Package-private: only {@link ShaclValidationPort}, in the same adapter module, needs the live
     * Jena model to read {@code sh:} shapes from — see plan §4 and §6.
     */
    OntModel currentModel(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return requireConsistent(current).model();
    }

    /**
     * Checks {@code fresh} for consistency, takes the version from {@code version} (which is only
     * called once the check has passed), and makes the result {@link #current}. If the build is
     * inconsistent it throws without touching {@link #current}, so a subsequent read still serves
     * whatever was there before.
     */
    private PortState adopt(Built fresh, Supplier<OntologyVersion> version) {
        checkConsistent(fresh.report());
        PortState adopted = fresh.stamp(version.get());
        current = adopted;
        return adopted;
    }

    private static PortState requireConsistent(PortState state) {
        checkConsistent(state.report());
        return state;
    }

    private static void checkConsistent(OntologyReport report) {
        if (!report.consistent()) {
            throw new OntologyException(report);
        }
    }

    private static Supplier<InputStream> streamOf(OntologyDocument document) {
        byte[] content = document.content().getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(content);
    }

    /**
     * Runs the full build pipeline against a freshly opened stream from {@code loader}, never
     * throwing a raw Jena exception: a Turtle parse failure or any exception raised while merging
     * the report and mapping the ontology is caught and turned into a {@link Built} carrying an
     * inconsistent {@link OntologyReport} with a single {@code ERROR} issue describing what went
     * wrong, rather than propagating past this adapter's boundary. Resolving {@code loader} itself
     * (e.g. {@link OntologySource#open}) is deliberately outside this try/catch — see {@link
     * #fromSource}'s javadoc on why a bad source location fails fast instead.
     */
    private Built buildState(Supplier<InputStream> loader) {
        InputStream in = loader.get();
        try (in) {
            OntModel model;
            try {
                model = OntModelBuilder.build(in, reasoner);
            } catch (RuntimeException parseFailure) {
                return failureState("Failed to parse the ontology: " + describe(parseFailure));
            }
            try {
                return buildStateFrom(model);
            } catch (RuntimeException mappingFailure) {
                return failureState("Failed to process the ontology: " + describe(mappingFailure));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the ontology source", e);
        }
    }

    /**
     * Merges every source of truth about {@code model} into one {@link OntologyReport} and maps the
     * three halves. See this class's javadoc for what is merged and why.
     */
    private static Built buildStateFrom(OntModel model) {
        List<OntologyIssue> issues = new ArrayList<>();
        boolean consistent = true;

        OntologyReport consistencyReport = ConsistencyChecker.check(model);
        issues.addAll(consistencyReport.issues());
        consistent &= consistencyReport.consistent();

        List<String> unresolvedImports = model.getID()
                .imports()
                .filter(iri -> !iri.equals(ImportResolver.SQ_META_IRI))
                .sorted()
                .toList();
        for (String iri : unresolvedImports) {
            issues.add(new OntologyIssue(
                    Severity.ERROR,
                    Optional.of(iri),
                    "owl:imports <" + iri + "> could not be resolved; this adapter only bundles <"
                            + ImportResolver.SQ_META_IRI + ">."));
            consistent = false;
        }

        Optional<String> reservedTermMessage = SqVocabulary.rejectionMessageIfReserved(model);
        if (reservedTermMessage.isPresent()) {
            issues.add(new OntologyIssue(Severity.ERROR, Optional.empty(), reservedTermMessage.get()));
            consistent = false;
        }

        MappingResult mapping = SnapshotMapper.map(model);
        issues.addAll(mapping.issues());
        consistent &= mapping.issues().stream().noneMatch(issue -> issue.severity() == Severity.ERROR);

        List<OntologyIssue> pluginIssues = unknownPluginIssues(mapping.types(), mapping.derivations());
        issues.addAll(pluginIssues);
        consistent &= pluginIssues.isEmpty();

        OntologyReport mergedReport = new OntologyReport(consistent, issues);

        String ontologyIri = model.getID().getURI();
        if (ontologyIri == null || ontologyIri.isBlank()) {
            ontologyIri = UNBUILDABLE_ONTOLOGY_IRI;
        }

        // Schema rejects two types with the same short name. Building it here, under a provisional
        // version, keeps that clash inside buildState's failure boundary. stamp() rebuilds it with
        // the version the build is adopted under.
        Schema schema = new Schema(
                FIRST_VERSION,
                ontologyIri,
                Optional.ofNullable(model.getID().getVersionIRI()),
                model.getNsPrefixMap(),
                mapping.types());

        return new Built(model, mergedReport, schema, mapping);
    }

    /**
     * Checks every {@code sq:Plugin}-derived property in {@code types} against the {@link
     * DerivationPlugin} implementations actually registered on the classpath via {@link
     * ServiceLoader}, so that a typo'd or never-implemented {@code sq:pluginName} is an {@code
     * ERROR} {@link OntologyIssue} at activation — not a lookup failure the first time someone reads
     * an object of that type. Resolved fresh on every call rather than cached on this instance:
     * caching would make a plug-in registered after this port's construction (e.g. a newly deployed
     * adapter jar) invisible to a later {@link #reload(Scope)}, which would be a surprising and
     * hard-to-diagnose staleness bug for the sake of an optimisation this method (one {@code
     * ServiceLoader.load} plus a walk of an already-in-memory type list) does not need.
     *
     * <p>Deduplicated by property IRI: a derived property inherited by {@code N} subtypes appears in
     * {@code N} types' {@link TypeDefinition#properties()} lists, but is one rule and must be one
     * issue, not {@code N}.
     *
     * @param types every type of the ontology, in the order its issues are reported
     * @param derivations the derivation rules, keyed by the IRI of the property each derives
     */
    private static List<OntologyIssue> unknownPluginIssues(
            List<TypeDefinition> types, Map<String, DerivationRule> derivations) {
        Set<String> registeredPluginNames = ServiceLoader.load(DerivationPlugin.class).stream()
                .map(ServiceLoader.Provider::get)
                .map(DerivationPlugin::name)
                .collect(Collectors.toSet());

        Map<String, String> pluginRulesByPropertyIri = new LinkedHashMap<>();
        for (TypeDefinition type : types) {
            for (PropertyDefinition property : type.properties()) {
                Optional.ofNullable(derivations.get(property.iri()))
                        .filter(PluginRule.class::isInstance)
                        .map(PluginRule.class::cast)
                        .ifPresent(rule -> pluginRulesByPropertyIri.putIfAbsent(property.iri(), rule.pluginName()));
            }
        }

        List<OntologyIssue> issues = new ArrayList<>();
        for (Map.Entry<String, String> entry : pluginRulesByPropertyIri.entrySet()) {
            String propertyIri = entry.getKey();
            String pluginName = entry.getValue();
            if (!registeredPluginNames.contains(pluginName)) {
                issues.add(new OntologyIssue(
                        Severity.ERROR,
                        Optional.of(propertyIri),
                        "sq:pluginName '" + pluginName + "' has no registered DerivationPlugin on the classpath."));
            }
        }
        return issues;
    }

    private static Built failureState(String message) {
        OntologyReport report =
                new OntologyReport(false, List.of(new OntologyIssue(Severity.ERROR, Optional.empty(), message)));
        Schema schema = new Schema(FIRST_VERSION, UNBUILDABLE_ONTOLOGY_IRI, Optional.empty(), Map.of(), List.of());
        MappingResult empty = new MappingResult(List.of(), Map.of(), Map.of(), Map.of(), List.of());
        return new Built(null, report, schema, empty);
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * One build of the ontology, before it is stamped with the version it is adopted under. {@code
     * schema} carries a provisional version (see {@link #buildStateFrom}); {@link #stamp} replaces
     * it.
     *
     * @param model the built model, needed by {@link #export(Scope, OntologyFormat)}; {@code null}
     *     only in a {@link #failureState} where building failed entirely, which is always paired
     *     with an inconsistent report, so {@link #requireConsistent} throws before anything
     *     dereferences a null model
     * @param report the merged report of this build
     * @param schema the Schema half, under a provisional version
     * @param mapping the Behaviour and Automation halves' content, and the types' derivations
     */
    private record Built(OntModel model, OntologyReport report, Schema schema, MappingResult mapping) {

        PortState stamp(OntologyVersion version) {
            return new PortState(
                    model,
                    report,
                    new Schema(version, schema.ontologyIri(), schema.versionIri(), schema.prefixes(), schema.types()),
                    new Behaviour(version, mapping.stateMachines(), mapping.derivations()),
                    new Automation(version, mapping.transitionActions()));
        }
    }

    /**
     * The atomically-swapped unit of state this port serves: the built {@link OntModel} (needed by
     * {@link #export(Scope, OntologyFormat)}), the merged {@link OntologyReport}, and the three
     * halves, all stamped with one {@link OntologyVersion}.
     */
    private record PortState(
            OntModel model, OntologyReport report, Schema schema, Behaviour behaviour, Automation automation) {}
}
