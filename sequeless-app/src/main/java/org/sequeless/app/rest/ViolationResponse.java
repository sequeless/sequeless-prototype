package org.sequeless.app.rest;

/**
 * JSON element of the {@code violations} array {@link ApiExceptionAdvice} renders for a {@code
 * ValidationException} (HTTP 400).
 *
 * <p>{@code property}/{@code propertyIri} are both {@code null} for a violation that applies to
 * the object as a whole rather than to any single property — {@code
 * org.sequeless.spi.validation.Violation#path()} blank, per that record's own javadoc — since
 * there is no property to name in that case.
 *
 * @param property the short name of the property the violation applies to, or {@code null} for an
 *     object-level violation
 * @param propertyIri the full IRI of the property the violation applies to, or {@code null} for an
 *     object-level violation
 * @param message a human-readable explanation of the violation
 */
public record ViolationResponse(String property, String propertyIri, String message) {}
