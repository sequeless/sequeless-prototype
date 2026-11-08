package org.sequeless.testkit.ontology;

import java.util.Objects;
import org.sequeless.spi.meta.automation.AutomationSource;
import org.sequeless.spi.meta.behaviour.BehaviourSource;
import org.sequeless.spi.meta.schema.SchemaSource;
import org.sequeless.spi.ontology.OntologyAdministration;

/**
 * The four ports one ontology implementation exposes to {@link OntologyContract}: the three read
 * ports, one per half of an activation, and the administration port. An implementation that
 * implements all four interfaces fills every component through {@link #of(SchemaSource)}.
 *
 * @param schemaSource the read port for the Schema half; must not be {@code null}
 * @param behaviourSource the read port for the Behaviour half; must not be {@code null}
 * @param automationSource the read port for the Automation half; must not be {@code null}
 * @param administration the port that reloads, exports and imports the ontology; must not be {@code
 *     null}
 */
public record OntologyPorts(
        SchemaSource schemaSource,
        BehaviourSource behaviourSource,
        AutomationSource automationSource,
        OntologyAdministration administration) {

    /**
     * Rejects a {@code null} component, so that a contract cannot run against a missing port.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public OntologyPorts {
        Objects.requireNonNull(schemaSource, "schemaSource must not be null");
        Objects.requireNonNull(behaviourSource, "behaviourSource must not be null");
        Objects.requireNonNull(automationSource, "automationSource must not be null");
        Objects.requireNonNull(administration, "administration must not be null");
    }

    /**
     * Builds the four ports from one object that implements all of them.
     *
     * @param <T> the implementation type
     * @param port the implementation under test; must not be {@code null}
     * @return a record whose four components are {@code port} itself
     * @throws NullPointerException if {@code port} is {@code null}
     */
    public static <T extends SchemaSource & BehaviourSource & AutomationSource & OntologyAdministration>
            OntologyPorts of(T port) {
        return new OntologyPorts(port, port, port, port);
    }
}
