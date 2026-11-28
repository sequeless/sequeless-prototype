package org.sequeless.app.query;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sequeless.kernel.values.Value;
import org.sequeless.spi.object.Audit;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.PropertyRef;
import org.sequeless.spi.object.TypeRef;
import org.sequeless.spi.query.ObjectView;

/**
 * Converts between the three object shapes the host's query path touches: a released object view, the prototype's
 * {@link ObjectView}, and the Command side's {@link BusinessObject} that the derivation planner computes on.
 *
 * <p>The released query has its own {@code TypeRef}, {@code PropertyRef} and {@code Audit} in {@code
 * org.sequeless.query.api}; the Command side keeps copies in {@code spi.object} (spec 17 decision Q1). The released to
 * prototype mapping happens here, at the host boundary, and nowhere else. The prototype view and the business object
 * carry the same eight components, so {@link #toView} and {@link #toBusinessObject} are exact and a round trip returns
 * an equal object. They moved here from {@code sequeless-core} in spec 18 U6 (D2), with the browse and read use cases.
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

    /**
     * @param object the object to view; must not be {@code null}
     * @return the view of {@code object}, with every component carried across unchanged
     */
    static ObjectView toView(BusinessObject object) {
        return new ObjectView(
                object.id(),
                object.type(),
                object.tenant(),
                object.version(),
                object.state(),
                object.properties(),
                object.audit(),
                object.deleted());
    }

    /**
     * @param view the view to rebuild an object from; must not be {@code null}
     * @return the object {@code view} describes, with every component carried across unchanged
     */
    static BusinessObject toBusinessObject(ObjectView view) {
        return new BusinessObject(
                view.id(),
                view.type(),
                view.tenant(),
                view.version(),
                view.state(),
                view.properties(),
                view.audit(),
                view.deleted());
    }

    private static Audit toAudit(org.sequeless.query.api.Audit audit) {
        return new Audit(audit.createdAt(), audit.createdBy(), audit.updatedAt(), audit.updatedBy());
    }
}
