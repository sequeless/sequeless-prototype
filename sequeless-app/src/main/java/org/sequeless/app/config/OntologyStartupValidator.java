package org.sequeless.app.config;

import java.util.Objects;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.schema.api.Schema;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Forces the active ontology to be read and validated once, at startup, and prepares query indexes
 * for it, so an inconsistent ontology fails the application before it ever accepts a request —
 * plan.md's Decision 4.
 *
 * <p><b>Why this exists at all.</b> {@link CoreConfiguration#metaModelService} injects its ports
 * {@code @Lazy}, for the ordering reason documented there: nothing reads the {@link SchemaSource}
 * eagerly, so on its own an inconsistent ontology would sit undetected until the first real request
 * reached {@code GET /types} or {@code /whoami}. This class is that first eager read, deliberately
 * placed outside {@code CoreConfiguration} so it does not reintroduce the pass-one/pass-two hazard
 * {@code @Lazy} exists to dodge there. The same call also seeds the query indexes for the seed
 * ontology, which no import or reload would otherwise have prepared (#24).
 *
 * <p><b>Why {@link ApplicationRunner} and not another lifecycle hook.</b> {@link
 * org.sequeless.app.port.PortRegistry} validates port cardinality in {@code
 * afterSingletonsInstantiated()} — the second of two {@code SmartInitializingSingleton} passes —
 * and this validator's own parameters are {@code @Lazy} proxies, so dereferencing them (by calling
 * {@link #run}) must happen only once that validation has already guaranteed exactly one port bean
 * exists. {@code ApplicationRunner}s execute after {@code refreshContext()} completes, which already
 * includes every {@code SmartInitializingSingleton} callback — so this runs strictly after {@code
 * PortRegistry}, with no {@code @Order} or other manual sequencing needed; Spring's own lifecycle
 * provides it.
 */
@Component
public class OntologyStartupValidator implements ApplicationRunner {

    private final SchemaSource schemaSource;
    private final IndexPreparation indexPreparation;

    /**
     * @param schemaSource the lazily-resolved schema source; must not be {@code null}
     * @param indexPreparation the host use case that prepares the seed ontology's indexes; must not
     *     be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public OntologyStartupValidator(@Lazy SchemaSource schemaSource, IndexPreparation indexPreparation) {
        this.schemaSource = Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        this.indexPreparation = Objects.requireNonNull(indexPreparation, "indexPreparation must not be null");
    }

    /**
     * Reads the Schema of the default tenant once, then prepares query indexes for the version of
     * that Schema. If the ontology is inconsistent, the {@link org.sequeless.ontology.api.OntologyException}
     * the read throws propagates out of {@code SpringApplication.run}, failing startup.
     *
     * @param args the application's startup arguments; unused
     */
    @Override
    public void run(ApplicationArguments args) {
        TenantId tenant = TenantId.DEFAULT;
        Schema schema = schemaSource.schema(tenant);
        indexPreparation.prepare(tenant, schema.ontologyVersion());
    }
}
