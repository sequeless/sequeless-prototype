package org.sequeless.adapter.automation.temporal;

import java.util.Objects;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.AutomationFailureRecorder;

/**
 * The real {@link AutomationFailureActivities} implementation: a pass-through to the injected
 * {@link AutomationFailureRecorder}. A failure of the write is not caught, so Temporal retries the
 * activity.
 */
public final class AutomationFailureActivitiesImpl implements AutomationFailureActivities {

    private final AutomationFailureRecorder recorder;

    /**
     * @param recorder the port that persists the record; must not be {@code null}
     */
    public AutomationFailureActivitiesImpl(AutomationFailureRecorder recorder) {
        this.recorder = Objects.requireNonNull(recorder, "recorder must not be null");
    }

    @Override
    public void recordFailure(AutomationFailure failure) {
        recorder.record(failure);
    }
}
