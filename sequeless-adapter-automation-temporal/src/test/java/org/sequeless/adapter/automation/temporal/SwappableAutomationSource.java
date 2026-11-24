package org.sequeless.adapter.automation.temporal;

import java.util.concurrent.atomic.AtomicInteger;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.ontology.automation.api.Automation;
import org.sequeless.ontology.automation.spi.AutomationSource;

/**
 * An {@link AutomationSource} test double whose automation a test swaps by assigning {@link #current}.
 *
 * <p>The state is static, not per instance, because the worker's activity implementation and the test
 * body each hold their own instance of this class, and the test body must see every call the activity
 * makes. Every test class that uses this double calls {@link #reset()} in its {@code @BeforeEach}.
 */
final class SwappableAutomationSource implements AutomationSource {

    /** The automation {@link #automation} returns. {@code null} until a test sets it. */
    static volatile Automation current;

    /** How many times {@link #automation} has been called since the last {@link #reset()}. */
    static final AtomicInteger calls = new AtomicInteger();

    /** When {@code true}, {@link #automation} throws a plain {@link RuntimeException} on every call. */
    static volatile boolean failEveryCall;

    @Override
    public Automation automation(TenantId tenantId) {
        calls.incrementAndGet();
        if (failEveryCall) {
            throw new RuntimeException("SwappableAutomationSource is set to fail every call");
        }
        return current;
    }

    /** Clears {@link #current}, {@link #calls} and {@link #failEveryCall}. */
    static void reset() {
        current = null;
        calls.set(0);
        failEveryCall = false;
    }
}
