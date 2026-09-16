package org.sequeless.spi.meta;

/**
 * Placeholder for the state-machine shape ({@code sq:StateMachine}: states, transitions, guards,
 * and actions) that Phase 5 (state machines and automation, DR-09) will define. {@link
 * TypeDefinition#stateMachine()} already carries an {@code Optional<StateMachineDefinition>} so
 * the snapshot shape does not need to change again once Phase 5 lands; this type exists only so
 * that slot compiles today, genuinely empty until then.
 */
public record StateMachineDefinition() {
}
