package org.sequeless.app.query;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;

/**
 * Maps a released object view to the prototype's. The released query has its own {@code TypeRef}, {@code PropertyRef}
 * and {@code Audit} in {@code org.sequeless.query.api}; the Command side keeps copies in {@code spi.object}
 * (spec 17 decision Q1). The mapping happens here, at the host boundary, and nowhere else.
 */
final class ObjectViewMapper {

    private ObjectViewMapper() {}

    /**
     * @param view a released object view; must not be {@code null}
     * @return the same object as the prototype's object view
     */
    static ObjectView toPrototype(org.sequeless.query.api.ObjectView view) {
        Map<PropertyRef, Value> properties = new LinkedHashMap<>();
        view.properties().forEach((ref, value) -> properties.put(new PropertyRef(ref.iri()), value));
        return new ObjectView(
                view.id(),
                new TypeRef(view.type().iri()),
                view.tenant(),
                view.version(),
                view.state(),
                properties,
                toAudit(view.audit()),
                view.deleted());
    }

    private static Audit toAudit(org.sequeless.query.api.Audit audit) {
        return new Audit(audit.createdAt(), audit.createdBy(), audit.updatedAt(), audit.updatedBy());
    }
}
