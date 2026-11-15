package org.sequeless.core.usecase;

import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.query.ObjectView;

/**
 * Converts between the Query side's {@link ObjectView} and the {@link BusinessObject} that {@link
 * org.sequeless.core.derivation.DerivationPlanner#apply} computes derived properties on. The two
 * records carry the same eight components, so each conversion is exact and a round trip returns an
 * equal object.
 *
 * <p>Package-private: only {@link DefaultBusinessObjectService} needs the conversion, and only for
 * its read and browse paths.
 */
final class ObjectViewMapper {

    private ObjectViewMapper() {}

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
}
