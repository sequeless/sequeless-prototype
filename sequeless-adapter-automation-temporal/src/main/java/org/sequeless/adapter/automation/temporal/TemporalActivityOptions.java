package org.sequeless.adapter.automation.temporal;

import io.temporal.activity.ActivityOptions;

/**
 * Holds the {@link ActivityOptions} every workflow implementation in this package builds its activity
 * stub with. The options carry the {@code RetryOptions} and timeout that {@link
 * TemporalAutomationAutoConfiguration} assembles from {@code sequeless.automation.temporal.retry.*}.
 *
 * <p>Temporal instantiates a workflow implementation itself, reflectively, once per workflow execution.
 * It therefore cannot receive these options through a constructor. Instead {@link
 * TemporalAutomationAutoConfiguration} publishes {@link #ACTIVITY_OPTIONS} once, before the worker
 * starts, and every later execution reads the same value. Nothing about a workflow's deterministic
 * replay depends on when the field was set, only on what it was set to when the workflow runs.
 *
 * <p>The field must not be read while it is still {@code null}. That would mean a worker started
 * without the auto-configuration having run, which is a wiring bug, so each reader throws on it.
 */
final class TemporalActivityOptions {

    /** The {@link ActivityOptions} every activity stub is built with, published once before worker start. */
    static volatile ActivityOptions ACTIVITY_OPTIONS;

    private TemporalActivityOptions() {}
}
