/**
 * Fail-fast adapter registry for sequeless-app.
 *
 * <p>Validates, at startup, that every configured port-selection property (such as {@code
 * sequeless.authz.adapter}) names a known adapter and that exactly one matching bean exists in the
 * application context — reporting a clear, actionable {@code PortBindingException} instead of
 * letting Spring fail with a generic wiring error. The types implementing this — {@code
 * PortDefinition}, {@code PortRegistry}, {@code PortBindingException}, and {@code
 * PortBindingFailureAnalyzer} — are added in a later task; this package exists from the start so
 * {@link org.sequeless.app.SequelessApplication}'s component scan already covers it.
 */
package org.sequeless.app.port;
