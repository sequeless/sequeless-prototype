package org.sequeless.app.port;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a {@link PortBindingException} anywhere in a startup failure's cause chain into Spring
 * Boot's console failure-analysis report, instead of a raw stack trace.
 *
 * <p>Extending {@link AbstractFailureAnalyzer} parameterized with {@link PortBindingException} is
 * what matters here, not any annotation: {@code AbstractFailureAnalyzer} reflectively resolves that
 * type parameter and walks the failure's {@code getCause()} chain looking for an instance of it, so
 * this analyzer fires whether Spring hands it the bare {@link PortBindingException} or a wrapped
 * one. It must be registered under the {@code org.springframework.boot.diagnostics.FailureAnalyzer}
 * key in {@code META-INF/spring.factories} to be discovered at all.
 */
public final class PortBindingFailureAnalyzer extends AbstractFailureAnalyzer<PortBindingException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, PortBindingException cause) {
        return new FailureAnalysis(cause.getMessage(), cause.action(), cause);
    }
}
