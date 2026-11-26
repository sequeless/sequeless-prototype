package org.sequeless.adapter.automation.temporal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.sequeless.spi.automation.AutomationFailure;
import org.sequeless.spi.automation.AutomationFailureRecorder;

/**
 * A test double for {@link AutomationFailureRecorder} that keeps each record in memory. A test can make the
 * write fail, to stand in for a database that is down, and then the failure propagates to the activity.
 */
final class RecordingAutomationFailureRecorder implements AutomationFailureRecorder {

    private final List<AutomationFailure> records = new CopyOnWriteArrayList<>();
    private volatile RuntimeException failWith;

    @Override
    public void record(AutomationFailure failure) {
        RuntimeException failure0 = failWith;
        if (failure0 != null) {
            throw failure0;
        }
        records.add(failure);
    }

    /** @return a snapshot of every record written, in the order written */
    List<AutomationFailure> records() {
        return List.copyOf(records);
    }

    /** @param failure the exception every later {@link #record} throws instead of writing */
    void failWith(RuntimeException failure) {
        this.failWith = failure;
    }

    /** Clears the records and stops any failure set by {@link #failWith}. */
    void reset() {
        records.clear();
        failWith = null;
    }
}
