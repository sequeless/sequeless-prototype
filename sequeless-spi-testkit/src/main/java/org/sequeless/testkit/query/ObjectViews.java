package org.sequeless.testkit.query;

import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.query.ObjectView;

/**
 * Renders a seeded {@link BusinessObject} as the {@link ObjectView} a query port must return for it,
 * so a contract can compare what a port answers with what was stored. This testkit does not depend
 * on sequeless-core, so the conversion is written here rather than shared with core's mapper.
 */
final class ObjectViews {

    private ObjectViews() {}

    /**
     * @param object the stored object; must not be {@code null}
     * @return the view with every component of {@code object} carried across unchanged
     */
    static ObjectView of(BusinessObject object) {
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
}
