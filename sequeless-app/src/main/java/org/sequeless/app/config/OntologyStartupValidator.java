package org.sequeless.app.config;

import java.util.Objects;
import org.sequeless.spi.Principal;
import org.sequeless.spi.Scope;
import org.sequeless.spi.TenantId;
import org.sequeless.spi.ontology.OntologyPort;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Forces the configured {@link OntologyPort} to be read and validated once, at startup, so an
 * inconsistent ontology fails the application before it ever accepts a request — plan.md's
 * Decision 4.
 *
 * <p><b>Why this exists at all.</b> {@link CoreConfiguration#metaModelService} injects both its
 * ports {@code @Lazy}, for the ordering reason documented there: nothing calls {@link
 * OntologyPort#snapshot(Scope)} eagerly, so on its own an inconsistent ontology would sit
 * undetected until the first real request reached {@code GET /types} or {@code /whoami}. This
 * class is that first eager call, deliberately placed outside {@code CoreConfiguration} so it
 * does not reintroduce the pass-one/pass-two hazard {@code @Lazy} exists to dodge there.
 *
 * <p><b>Why {@link ApplicationRunner} and not another lifecycle hook.</b> {@link
 * org.sequeless.app.port.PortRegistry} validates port cardinality in {@code
 * afterSingletonsInstantiated()} — the second of two {@code SmartInitializingSingleton} passes —
 * and this validator's own {@link OntologyPort} parameter is itself a {@code @Lazy} proxy, so
 * dereferencing it (by calling {@link #run}) must happen only once that validation has already
 * guaranteed exactly one {@link OntologyPort} bean exists. {@code ApplicationRunner}s execute
 * after {@code refreshContext()} completes, which already includes every {@code
 * SmartInitializingSingleton} callback — so this runs strictly after {@code PortRegistry}, with no
 * {@code @Order} or other manual sequencing needed; Spring's own lifecycle provides it.
 */
@Component
public class OntologyStartupValidator implements ApplicationRunner {

    private final OntologyPort ontologyPort;

    /**
     * @param ontologyPort the lazily-resolved ontology port adapter; must not be {@code null}
     * @throws NullPointerException if {@code ontologyPort} is {@code null}
     */
    public OntologyStartupValidator(@Lazy OntologyPort ontologyPort) {
        this.ontologyPort = Objects.requireNonNull(ontologyPort, "ontologyPort must not be null");
    }

    /**
     * Reads and validates the ontology once. If it is inconsistent, the {@link
     * org.sequeless.spi.ontology.OntologyException} this throws propagates out of {@code
     * SpringApplication.run}, failing startup.
     *
     * @param args the application's startup arguments; unused
     */
    @Override
    public void run(ApplicationArguments args) {
        ontologyPort.snapshot(new Scope(TenantId.DEFAULT, Principal.ANONYMOUS));
    }
}
