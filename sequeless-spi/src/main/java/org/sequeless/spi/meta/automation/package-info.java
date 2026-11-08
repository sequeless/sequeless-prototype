/**
 * The Automation half of an ontology activation: the actions that run when a transition fires.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) and the kernel, except sibling types in {@code org.sequeless.spi.meta.schema}
 * and {@code org.sequeless.spi.meta.behaviour}. Every action is sealed to {@link
 * org.sequeless.spi.meta.automation.SetPropertyAction} ({@code sq:SetProperty}), {@link
 * org.sequeless.spi.meta.automation.CreateObjectAction} ({@code sq:CreateObject}), {@link
 * org.sequeless.spi.meta.automation.WebhookAction} ({@code sq:Webhook}), or {@link
 * org.sequeless.spi.meta.automation.LogAction} ({@code sq:Log}) through the {@link
 * org.sequeless.spi.meta.automation.Action} sealed interface, so a {@code switch} over an action is
 * exhaustive without a default case.
 *
 * <p>{@link org.sequeless.spi.meta.automation.Automation} is the aggregate of this half. It files the
 * actions of each transition under a {@link org.sequeless.spi.meta.automation.TransitionRef}; a
 * transition without actions has no entry. {@link org.sequeless.spi.meta.automation.AutomationSource}
 * is the read port.
 *
 * <p>The triggers that make a transition fire stay on the behaviour half, on {@code
 * org.sequeless.spi.meta.behaviour.Transition}.
 */
package org.sequeless.spi.meta.automation;
