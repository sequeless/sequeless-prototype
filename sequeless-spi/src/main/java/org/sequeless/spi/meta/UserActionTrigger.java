package org.sequeless.spi.meta;

/**
 * A {@link TriggerSpec} whose only trigger is a user's own {@code POST
 * /objects/{type}/{id}/transitions/{name}} request ({@code sq:UserAction}), exactly as {@code
 * activate} moves an {@code ex:Project} from {@code ex:Draft} to {@code ex:Active} only when a
 * caller asks for it. This is the trigger every transition had, implicitly, before this phase
 * introduced the other three — it carries no fields because "a REST call named this transition"
 * is the entire condition. {@code DefaultTransitionService.fire} (the REST-triggered path) rejects
 * any transition whose {@link Transition#trigger()} is not this variant, since {@code
 * fireAutomated} exists precisely to fire the other three kinds instead.
 */
public record UserActionTrigger() implements TriggerSpec {

    @Override
    public TriggerKind kind() {
        return TriggerKind.USER_ACTION;
    }
}
