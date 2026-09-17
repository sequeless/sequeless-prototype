package org.sequeless.spi.query;

/**
 * A comparison a {@link Criterion} applies to a property's value. Each constant's javadoc notes
 * which datatypes it is typically meaningful for; this is informational only, not enforced by
 * this package — {@link Criterion} itself does not check operator/datatype coherence, and a later
 * core-layer validation task is responsible for rejecting a nonsensical pairing (such as {@code
 * GT} against a boolean-typed property) before it ever reaches a {@link QueryPort}.
 */
public enum Operator {

    /** Equality. Applies to every datatype, including references. */
    EQ,

    /** Inequality. Applies to every datatype, including references. */
    NE,

    /** Membership in a set of candidate values. Applies to every datatype, including references. */
    IN,

    /** Strictly less than. Typically applies to numeric, date, and date-time properties. */
    LT,

    /** Less than or equal to. Typically applies to numeric, date, and date-time properties. */
    LTE,

    /** Strictly greater than. Typically applies to numeric, date, and date-time properties. */
    GT,

    /**
     * Greater than or equal to. Typically applies to numeric, date, and date-time properties.
     */
    GTE,

    /** Substring match. Typically applies to text-valued properties. */
    CONTAINS,

    /** Prefix match. Typically applies to text-valued properties. */
    STARTS_WITH,

    /** Whether the property has no value. Applies to every datatype. */
    IS_NULL,

    /** Whether the property has a value. Applies to every datatype. */
    NOT_NULL
}
