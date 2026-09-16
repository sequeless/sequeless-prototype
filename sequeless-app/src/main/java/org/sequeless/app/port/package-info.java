/**
 * Fail-fast adapter registry for sequeless-app.
 *
 * <p>Validates, at startup, that every configured port-selection property (such as {@code
 * sequeless.authz.adapter}) names a known adapter and that exactly one matching bean exists in the
 * application context — reporting a clear, actionable {@link PortBindingException} instead of
 * letting Spring fail with a generic wiring error. Contains {@link PortDefinition}, {@link
 * PortRegistry}, {@link PortBindingException}, and {@link PortBindingFailureAnalyzer}. This
 * package exists from the start so {@link org.sequeless.app.SequelessApplication}'s component
 * scan already covers it.
 */
package org.sequeless.app.port;
