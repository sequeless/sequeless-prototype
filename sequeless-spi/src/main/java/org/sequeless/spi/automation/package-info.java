/**
 * The automation pieces the prototype host keeps: the derivation recompute port ({@link
 * org.sequeless.spi.automation.DerivationRecomputer}) and the failure record ({@link
 * org.sequeless.spi.automation.AutomationFailure} and {@link
 * org.sequeless.spi.automation.AutomationFailureRecorder}).
 *
 * <p>The automation ports and Store event types moved to {@code sequeless-automation} in spec 17
 * P4: they are now {@code org.sequeless.automation.api}. The move is recorded in
 * {@code docs/extraction/spec-17-prototype-stage4-cutover.md}, section 2.4.
 */
package org.sequeless.spi.automation;
