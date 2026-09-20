/**
 * The test/dev {@code AutomationPort} adapter, used in place of Temporal for tests and local
 * development: {@link org.sequeless.adapter.automation.inprocess.InProcessAutomationPort} routes
 * an {@code ActionRequest} outbox entry to the matching {@code ActionExecutor} method
 * synchronously, on the calling (relay) thread, including a small fixed retry loop around a {@code
 * sq:Webhook} action's actual HTTP call (via the JDK's own {@code java.net.http.HttpClient}, no
 * new dependency). Unlike a future Temporal adapter, this one owns idempotency itself — an
 * in-memory, process-lifetime {@code Set} of already-dispatched {@code OutboxEntry} ids — since it
 * has no workflow-id mechanism to get that guarantee for free; see that class's own javadoc for
 * the accepted limitations this implies. {@link
 * org.sequeless.adapter.automation.inprocess.InProcessAutomationAutoConfiguration} wires the port
 * bean when {@code sequeless.automation.adapter=inprocess}, taking an already-registered {@code
 * ActionExecutor} bean as a method parameter rather than constructing one itself (this module
 * cannot depend on {@code sequeless-core}, where the real implementation lives).
 */
package org.sequeless.adapter.automation.inprocess;
