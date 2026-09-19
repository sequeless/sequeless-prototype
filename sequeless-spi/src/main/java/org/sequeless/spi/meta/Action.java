package org.sequeless.spi.meta;

/**
 * A single action dispatched when a {@link Transition} fires: {@link SetPropertyAction} ({@code
 * sq:SetProperty}), {@link CreateObjectAction} ({@code sq:CreateObject}), {@link WebhookAction}
 * ({@code sq:Webhook}), or {@link LogAction} ({@code sq:Log}). Sealed to exactly these four
 * permitted implementations, mirroring {@link DerivationRule}, so a {@code switch} over {@code
 * Action} is exhaustive without a default case.
 *
 * <p>{@code permits} is declared explicitly rather than left implicit because each permitted type
 * lives in its own file, not nested inside this one; implicit permits only works for subtypes
 * nested in the sealed type's own file.
 */
public sealed interface Action permits SetPropertyAction, CreateObjectAction, WebhookAction, LogAction {
}
