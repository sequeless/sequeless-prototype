package org.sequeless.app.query;

import java.util.List;
import org.sequeless.query.api.Direction;
import org.sequeless.query.api.Sort;
import org.sequeless.query.field.api.FieldScope;

/**
 * Maps the prototype's property-IRI sorts to the released {@link Sort}s, which name a {@link
 * org.sequeless.query.field.api.FieldPath}. A sort on a property the released scope marks not sortable is still
 * mapped: the released service rejects it with its own {@code InvalidQueryException}, and this mapper does not
 * swallow that.
 */
final class SortMapper {

    private SortMapper() {}

    /**
     * @param sorts the prototype sorts, in order; must not be {@code null}
     * @param scope the scope resolved for the browse's root class; must not be {@code null}
     * @return one released sort per prototype sort, in the same order
     */
    static List<Sort> toReleased(List<org.sequeless.spi.query.Sort> sorts, FieldScope scope) {
        return sorts.stream()
                .map(sort -> new Sort(
                        DirectFieldPaths.of(scope, sort.property()),
                        Direction.valueOf(sort.direction().name())))
                .toList();
    }
}
