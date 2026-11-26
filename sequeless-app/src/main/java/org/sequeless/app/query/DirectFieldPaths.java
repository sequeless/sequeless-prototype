package org.sequeless.app.query;

import java.util.List;
import org.sequeless.query.api.InvalidQueryException;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.FieldScope;

/**
 * Finds the released {@link FieldPath} of a direct property, by its IRI, among the facets of a resolved {@link
 * FieldScope}. The prototype names sorts and facets by property IRI; the released query names them by path, and
 * the path carries the property's id, slug and target class, which only the resolved scope holds.
 */
final class DirectFieldPaths {

    private DirectFieldPaths() {}

    /**
     * @param scope the scope resolved for the browse's root class; must not be {@code null}
     * @param propertyIri the IRI of a property of the root class; must not be {@code null}
     * @return the path of the facet whose single segment has that IRI; never a traversal
     * @throws InvalidQueryException if the scope has no facet for that IRI
     */
    static FieldPath of(FieldScope scope, String propertyIri) {
        return scope.facets().stream()
                .map(Field::path)
                .filter(path ->
                        path.depth() == 1 && path.segments().get(0).iri().equals(propertyIri))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryException(
                        List.of("property '" + propertyIri + "' is not a field of the browsed type")));
    }
}
