package org.sequeless.app.automation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.sequeless.automation.api.ObjectSnapshot;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.query.ObjectView;

/**
 * Maps the Command side's {@link ObjectView} to the released {@link ObjectSnapshot} that {@code ObjectReadPort}
 * returns. The type and the state become IRI strings, and the property values are keyed by property IRI, so no
 * Command-side type leaves this package (spec 17, section 10, Q1).
 */
final class ObjectSnapshots {

    private ObjectSnapshots() {}

    /**
     * @param view the view to map; must not be {@code null}
     * @return the snapshot of the same object: its type IRI, its state, and its property values keyed by IRI
     */
    static ObjectSnapshot from(ObjectView view) {
        Objects.requireNonNull(view, "view must not be null");
        Map<String, Value> properties = new HashMap<>();
        for (Map.Entry<PropertyRef, Value> entry : view.properties().entrySet()) {
            properties.put(entry.getKey().iri(), entry.getValue());
        }
        return new ObjectSnapshot(view.type().iri(), view.state(), properties);
    }
}
