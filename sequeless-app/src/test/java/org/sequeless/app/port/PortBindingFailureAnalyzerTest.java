package org.sequeless.app.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.sequeless.spi.AdapterDescriptor;
import org.sequeless.spi.authz.AuthorizationPort;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Plain unit test for {@link PortBindingFailureAnalyzer}: constructs a {@link
 * PortBindingException} directly and asserts the analyzer copies its message and action into the
 * {@link FailureAnalysis} verbatim.
 */
class PortBindingFailureAnalyzerTest {

    @Test
    void analyzeCopiesMessageAndActionFromException() {
        PortDefinition definition = new PortDefinition("sequeless.authz.adapter", AuthorizationPort.class);
        List<AdapterDescriptor> available =
            List.of(new AdapterDescriptor("permit-all", AuthorizationPort.class, "sequeless.authz.adapter", ">=0.1.0"));
        PortBindingException exception = PortBindingException.unknownAdapter("missing", definition, available);

        FailureAnalysis analysis = new PortBindingFailureAnalyzer().analyze(exception);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).isEqualTo(exception.getMessage());
        assertThat(analysis.getAction()).isEqualTo(exception.action());
        assertThat(analysis.getCause()).isSameAs(exception);
    }
}
