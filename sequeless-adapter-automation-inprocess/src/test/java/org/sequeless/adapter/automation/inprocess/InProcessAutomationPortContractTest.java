package org.sequeless.adapter.automation.inprocess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.sequeless.spi.automation.AutomationPort;
import org.sequeless.spi.automation.ResolvedWebhookRequest;
import org.sequeless.testkit.automation.AutomationContract;
import org.sequeless.testkit.automation.RecordingActionExecutor;
import org.sequeless.testkit.automation.RecordingDerivationRecomputer;
import org.sequeless.testkit.automation.RecordingTriggerEvaluator;

/**
 * Proves {@link InProcessAutomationPort} satisfies every clause of {@link AutomationPort}'s
 * behavioural contract, including {@link AutomationContract}'s idempotent-redispatch test: this
 * adapter's own {@code Set<UUID>} guard (not any built-in mechanism, since it has none) is what
 * makes that test pass.
 *
 * <p>Unlike {@link AutomationContract}'s own default {@link RecordingActionExecutor} constructor
 * (which points at {@code http://example.invalid/hook} — deliberately unresolvable per RFC 2606,
 * fine for a test double that never actually issues HTTP calls), this class wires {@link
 * #recorder} to a real local {@link HttpServer} that always answers 200: {@link
 * InProcessAutomationPort}, unlike the shared contract's hypothetical adapter, always performs a
 * genuine HTTP delivery inside {@code dispatch}, so {@link
 * AutomationContract#dispatchRoutesWebhookToResolveWebhook} would otherwise fail here not because
 * routing was wrong, but because delivery to an intentionally unresolvable host cannot succeed.
 */
class InProcessAutomationPortContractTest extends AutomationContract {

    private static HttpServer server;
    private static String webhookUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(
            "/hook",
            exchange -> {
                exchange.getRequestBody().readAllBytes();
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
        server.start();
        webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/hook";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private final RecordingActionExecutor recorder =
        new RecordingActionExecutor(new ResolvedWebhookRequest(webhookUrl, "POST", Optional.empty()));
    private final RecordingTriggerEvaluator triggerEvaluator = new RecordingTriggerEvaluator();
    private final RecordingDerivationRecomputer derivationRecomputer = new RecordingDerivationRecomputer();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InProcessTimerScheduler timerScheduler = new InProcessTimerScheduler(clock, triggerEvaluator);
    private final AutomationPort port =
        new InProcessAutomationPort(
            recorder,
            new InProcessAutomationProperties(),
            triggerEvaluator,
            derivationRecomputer,
            InProcessRecomputeProperties.Mode.RETRY_ONLY,
            timerScheduler);

    @Override
    protected AutomationPort port() {
        return port;
    }

    @Override
    protected RecordingActionExecutor recordingExecutor() {
        return recorder;
    }

    @Override
    protected RecordingTriggerEvaluator recordingTriggerEvaluator() {
        return triggerEvaluator;
    }

    @Override
    protected RecordingDerivationRecomputer recordingDerivationRecomputer() {
        return derivationRecomputer;
    }

    @Override
    protected void advanceTime(Duration by) {
        clock.advance(by);
        timerScheduler.sweep();
    }
}
