/**
 * Structural validation: turning raw, JSON-like input into typed {@link org.sequeless.spi.object
 * .Value}s and checking the result against the meta-model's shape rules, independent of any
 * ontology-specific SHACL constraint.
 *
 * <p>This sits beside {@link org.sequeless.core.api} and {@link org.sequeless.core.usecase} as a
 * third, sibling package rather than inside either of them: {@link
 * org.sequeless.core.validation.ValueCoercer}, {@link org.sequeless.core.validation
 * .StructuralValidator}, and {@link org.sequeless.core.validation.TypeHierarchy} are not an
 * inbound use-case contract (so they do not belong in {@code api}), and they are not a {@code
 * Default...} implementation of one (so they do not belong in {@code usecase}) — they are
 * stateless helpers that {@code usecase} classes such as {@code DefaultBusinessObjectService}
 * call into.
 *
 * <p>{@link org.sequeless.core.validation.ValueCoercer#coerce} turns a {@code Map<String,
 * Object>} of raw property input into a {@link org.sequeless.core.validation.CoercionResult}: a
 * fully typed {@code Map<PropertyRef, Value>} on success, or the {@link
 * org.sequeless.spi.validation.Violation}s found on failure — unknown properties, read-only
 * properties supplied by the caller, datatype mismatches, and scalar/list cardinality mismatches.
 * {@link org.sequeless.core.validation.StructuralValidator} then checks the coerced properties
 * against the meta-model's required-property and cardinality rules, and against reference
 * integrity via {@code ObjectStorePort.typesOf}. {@link org.sequeless.core.validation
 * .TypeHierarchy} is the shared subtype-walking helper both of those, and {@code
 * DefaultBusinessObjectService}, need. {@link org.sequeless.core.validation.ValidationException}
 * is the unchecked exception a use case throws when either the structural checks here, or the
 * {@code ValidationPort} SHACL check that follows them, finds a violation.
 */
package org.sequeless.core.validation;
