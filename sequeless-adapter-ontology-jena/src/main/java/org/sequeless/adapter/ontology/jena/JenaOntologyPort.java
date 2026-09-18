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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.jena.ontapi.model.OntModel;
import org.sequeless.spi.Scope;
import org.sequeless.spi.derivation.DerivationPlugin;
import org.sequeless.spi.meta.MetaModelSnapshot;
import org.sequeless.spi.meta.PluginRule;
import org.sequeless.spi.meta.PropertyDefinition;
import org.sequeless.spi.meta.TypeDefinition;
import org.sequeless.spi.object.OntologyDocumentStore;
import org.sequeless.spi.object.StoredOntologyDocument;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyIssue;
import org.sequeless.spi.ontology.OntologyPort;
import org.sequeless.spi.ontology.OntologyReport;
import org.sequeless.spi.ontology.Severity;

/**
 * The default {@link OntologyPort} adapter, backed by Apache Jena's {@code ontapi}. Two static
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
 * → map-to-snapshot pipeline against a *new*, private {@link PortState} value, touching nothing
 * this port has previously published. Only once that fresh state exists in hand does the caller
 * decide what to do with it:
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
 * record — never two separate fields for "the model" and "the snapshot". If those lived in
 * separate fields, a thread calling {@link #snapshot(Scope)} or {@link #export(Scope,
 * OntologyFormat)} could observe one field already swapped to the new build and the other still
 * holding the old one — a torn pair that belongs to neither the old ontology nor the new one. A
 * single reference swap on a {@code volatile} field is atomic with respect to every other thread by
 * the Java Memory Model, so any reader always sees either entirely-old or entirely-new state, never
 * a mixture, and never needs a lock to read it.
 *
 * <h2>{@code fromDocument} never throws for a bad document (F20)</h2>
 *
 * <p>The constructor calls {@link #buildState(Supplier)} and unconditionally assigns the result to
 * {@link #current}, even when the freshly built ontology is inconsistent or could not even be
 * parsed. Every judgement about whether the loaded ontology is *usable* — thrown as {@link
 * OntologyException} — is deferred to {@link #snapshot(Scope)}, {@link #export(Scope,
 * OntologyFormat)}, {@link #reload(Scope)} and {@link #importDocument(Scope, OntologyDocument,
 * ImportMode)}, exactly as {@link OntologyPort}'s own javadoc requires. This is not merely a style
 * choice: {@code OntologyContract}'s {@code inconsistentOntologyMakesSnapshotThrow} test calls
 * {@code portFor(inconsistentDocument)} completely unguarded and only wraps the subsequent {@code
 * snapshot()} call in an assertion — a constructor that throws for a bad document would already
 * fail that test before the assertion is even reached.
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
 * OntologyPort}'s contract or {@code OntologyContract} exercises this interaction, but it is worth
 * a future test knowing about explicitly rather than discovering by surprise.
 *
 * <p>With a {@link #documentStore} — the {@link #fromStore} construction path — {@link
 * #reload(Scope)} instead re-reads whatever document is currently active in the store for the
 * given {@code scope}, which may differ from both {@link #sourceLoader} and this port's own
 * in-memory copy if something changed the store out-of-band (another instance's {@link
 * #importDocument}, or a direct write to the store).
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
 * #unknownPluginIssues(List)}'s {@code ServiceLoader}-backed check that every {@code sq:Plugin}
 * rule's {@code sq:pluginName} actually resolves to a registered {@link
 * org.sequeless.spi.derivation.DerivationPlugin}, into the single {@link OntologyReport} that ends
 * up inside the {@link MetaModelSnapshot} this port returns.
 */
public final class JenaOntologyPort implements OntologyPort {

    /**
     * Used only when the ontology could not be built or mapped at all (a parse failure, or an
     * exception from {@link MetaModelSnapshot}'s own construction, e.g. a short-name collision) —
     * {@link MetaModelSnapshot#ontologyIri()} must not be blank, and in these failure cases there is
     * no real ontology IRI to report.
     */
    private static final String UNBUILDABLE_ONTOLOGY_IRI = "urn:sequeless:jena-adapter:unbuildable-ontology";

    private final Supplier<InputStream> sourceLoader;
    private final ReasonerSetting reasoner;
    private final Optional<OntologyDocumentStore> documentStore;
    private volatile PortState current;

    private JenaOntologyPort(
            Supplier<InputStream> sourceLoader, ReasonerSetting reasoner, Optional<OntologyDocumentStore> documentStore) {
        this.sourceLoader = sourceLoader;
        this.reasoner = reasoner;
        this.documentStore = documentStore;
        this.current = buildState(sourceLoader);
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
        byte[] content = document.content().getBytes(StandardCharsets.UTF_8);
        return new JenaOntologyPort(() -> new ByteArrayInputStream(content), reasoner, Optional.empty());
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
        return new JenaOntologyPort(() -> OntologySource.open(propertyName, location), reasoner, Optional.empty());
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
            OntologyDocumentStore documents, Scope scope, String sourcePropertyName, String source,
            ReasonerSetting reasoner) {
        Objects.requireNonNull(documents, "documents must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(reasoner, "reasoner must not be null");

        Optional<StoredOntologyDocument> active = documents.active(scope);
        OntologyDocument initial;
        if (active.isPresent()) {
            initial = active.get().document();
        } else {
            if (source == null || source.isBlank()) {
                throw new IllegalStateException(
                    "No ontology document stored for tenant '" + scope.tenantId().value()
                        + "' and no '" + sourcePropertyName + "' configured to seed one");
            }
            Objects.requireNonNull(sourcePropertyName, "sourcePropertyName must not be null");
            String turtle;
            try (InputStream in = OntologySource.open(sourcePropertyName, source)) {
                turtle = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read the ontology source to seed the store", e);
            }
            initial = new OntologyDocument(turtle, OntologyFormat.TURTLE);
            JenaOntologyPort probe = fromDocument(initial, reasoner);
            requireConsistent(probe.current); // throws OntologyException; never activates a broken seed
            documents.activate(scope, initial);
        }

        byte[] content = initial.content().getBytes(StandardCharsets.UTF_8);
        return new JenaOntologyPort(
            () -> new ByteArrayInputStream(content), reasoner, Optional.of(documents));
    }

    @Override
    public MetaModelSnapshot snapshot(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return requireConsistent(current).snapshot();
    }

    @Override
    public MetaModelSnapshot reload(Scope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        Supplier<InputStream> loader =
            documentStore.<Supplier<InputStream>>map(store -> () -> activeDocumentStream(store, scope))
                .orElse(sourceLoader);
        return swapOnSuccess(buildState(loader)).snapshot();
    }

    private static InputStream activeDocumentStream(OntologyDocumentStore store, Scope scope) {
        StoredOntologyDocument active = store.active(scope)
            .orElseThrow(() -> new IllegalStateException(
                "No active ontology document stored for tenant '" + scope.tenantId().value() + "'"));
        byte[] bytes = active.document().content().getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public OntologyDocument export(Scope scope, OntologyFormat format) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(format, "format must not be null");
        return TurtleExporter.export(requireConsistent(current).model());
    }

    @Override
    public ImportReport importDocument(Scope scope, OntologyDocument document, ImportMode mode) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(mode, "mode must not be null");
        if (mode != ImportMode.REPLACE) {
            // ImportMode currently declares only REPLACE; this guards the day it declares MERGE too.
            throw new IllegalArgumentException("Unsupported ImportMode: " + mode);
        }

        byte[] content = document.content().getBytes(StandardCharsets.UTF_8);
        PortState fresh = buildState(() -> new ByteArrayInputStream(content));
        requireConsistent(fresh);
        documentStore.ifPresent(store -> store.activate(scope, document));
        current = fresh;
        return new ImportReport(true, fresh.snapshot().report(), fresh.snapshot().types().size());
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
     * Adopts {@code fresh} as {@link #current} only if it is consistent, per the class-level "build
     * fresh, validate, swap only on success" discipline; otherwise throws without touching {@link
     * #current}, so a subsequent {@link #snapshot(Scope)} still serves whatever was there before.
     */
    private PortState swapOnSuccess(PortState fresh) {
        requireConsistent(fresh);
        current = fresh;
        return fresh;
    }

    private static PortState requireConsistent(PortState state) {
        OntologyReport report = state.snapshot().report();
        if (!report.consistent()) {
            throw new OntologyException(report);
        }
        return state;
    }

    /**
     * Runs the full build pipeline against a freshly opened stream from {@code loader}, never
     * throwing a raw Jena exception: a Turtle parse failure or any exception raised while merging
     * the report and mapping the snapshot is caught and turned into a {@link PortState} carrying an
     * inconsistent {@link OntologyReport} with a single {@code ERROR} issue describing what went
     * wrong, rather than propagating past this adapter's boundary. Resolving {@code loader} itself
     * (e.g. {@link OntologySource#open}) is deliberately outside this try/catch — see {@link
     * #fromSource}'s javadoc on why a bad source location fails fast instead.
     */
    private PortState buildState(Supplier<InputStream> loader) {
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
     * Merges every source of truth about {@code model} into one {@link OntologyReport} and builds
     * the {@link MetaModelSnapshot} around it. See this class's javadoc for what is merged and why.
     */
    private PortState buildStateFrom(OntModel model) {
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

        List<OntologyIssue> pluginIssues = unknownPluginIssues(mapping.types());
        issues.addAll(pluginIssues);
        consistent &= pluginIssues.isEmpty();

        OntologyReport mergedReport = new OntologyReport(consistent, issues);

        String ontologyIri = model.getID().getURI();
        if (ontologyIri == null || ontologyIri.isBlank()) {
            ontologyIri = UNBUILDABLE_ONTOLOGY_IRI;
        }

        MetaModelSnapshot snapshot = new MetaModelSnapshot(
            ontologyIri,
            Optional.ofNullable(model.getID().getVersionIRI()),
            model.getNsPrefixMap(),
            mapping.types(),
            mergedReport);

        return new PortState(model, snapshot);
    }

    /**
     * Checks every {@code sq:Plugin}-derived property in {@code types} against the {@link
     * DerivationPlugin} implementations actually registered on the classpath via {@link
     * ServiceLoader}, so that a typo'd or never-implemented {@code sq:pluginName} is an {@code
     * ERROR} {@link OntologyIssue} at snapshot activation — not a lookup failure the first time
     * someone reads an object of that type. Resolved fresh on every call rather than cached on this
     * instance: caching would make a plug-in registered after this port's construction (e.g. a
     * newly deployed adapter jar) invisible to a later {@link #reload(Scope)}, which would be a
     * surprising and hard-to-diagnose staleness bug for the sake of an optimisation this method
     * (one {@code ServiceLoader.load} plus a walk of an already-in-memory type list) does not need.
     *
     * <p>Deduplicated by property IRI: a derived property inherited by {@code N} subtypes appears in
     * {@code N} types' {@link TypeDefinition#properties()} lists, but is one rule and must be one
     * issue, not {@code N}.
     */
    private static List<OntologyIssue> unknownPluginIssues(List<TypeDefinition> types) {
        Set<String> registeredPluginNames = ServiceLoader.load(DerivationPlugin.class).stream()
            .map(ServiceLoader.Provider::get)
            .map(DerivationPlugin::name)
            .collect(Collectors.toSet());

        Map<String, String> pluginRulesByPropertyIri = new LinkedHashMap<>();
        for (TypeDefinition type : types) {
            for (PropertyDefinition property : type.properties()) {
                property.derivation()
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

    private static PortState failureState(String message) {
        OntologyReport report =
            new OntologyReport(false, List.of(new OntologyIssue(Severity.ERROR, Optional.empty(), message)));
        MetaModelSnapshot snapshot =
            new MetaModelSnapshot(UNBUILDABLE_ONTOLOGY_IRI, Optional.empty(), Map.of(), List.of(), report);
        return new PortState(null, snapshot);
    }

    private static String describe(RuntimeException e) {
        String message = e.getMessage();
        return e.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    /**
     * The atomically-swapped unit of state this port serves: the built {@link OntModel} (needed by
     * {@link #export(Scope, OntologyFormat)}; {@code null} only in a {@link #failureState} where
     * building failed entirely, which is always paired with an inconsistent {@link #snapshot}, so
     * {@link #requireConsistent} throws before anything dereferences a null model) and the {@link
     * MetaModelSnapshot} — including its merged {@link OntologyReport} — built from it.
     */
    private record PortState(OntModel model, MetaModelSnapshot snapshot) {}
}
