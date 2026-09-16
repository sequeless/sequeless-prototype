package org.sequeless.spi.meta;

import java.util.Objects;
import java.util.Optional;

/**
 * Presentation guidance carried by both {@link TypeDefinition} and {@link PropertyDefinition}: how
 * to order it relative to its siblings, which named group to show it under, and whether to hide it
 * from ordinary presentation entirely. This is the snapshot-side home for the {@code sq:}
 * annotation terms that OWL itself has no vocabulary for — see {@code sq:displayOrder}, {@code
 * sq:displayGroup}, and {@code sq:hidden} in the vocabulary specification.
 *
 * @param order sort position among siblings, lower first; {@link Integer#MAX_VALUE} sorts last and
 *     is the default for anything without an explicit {@code sq:displayOrder}
 * @param group the named display group, if any; must not be {@code null} (the {@link Optional}
 *     wrapper itself, not just its contents)
 * @param hidden whether this type or property should be hidden from ordinary presentation
 */
public record DisplayHints(int order, Optional<String> group, boolean hidden) {

    public DisplayHints {
        Objects.requireNonNull(group, "group must not be null");
    }

    /**
     * @return the default hints for a type or property with no explicit {@code sq:} display
     *     annotations: sorts last, no group, not hidden
     */
    public static DisplayHints none() {
        return new DisplayHints(Integer.MAX_VALUE, Optional.empty(), false);
    }
}
