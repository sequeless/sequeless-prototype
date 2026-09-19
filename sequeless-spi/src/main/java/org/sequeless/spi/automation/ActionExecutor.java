package org.sequeless.spi.automation;

import org.sequeless.spi.Scope;
import org.sequeless.spi.object.OutboxEntry;

/**
 * The inbound port an automation adapter (the in-process relay, or a Temporal activity) calls back
 * into to apply one fired transition's action, using the exact same validated, audited code path
 * {@code sequeless-core} already uses for every other write. See this package's {@code
 * package-info.java} for why this port's dependency direction is the mirror image of every other
 * port in this SPI: {@code sequeless-core} implements this interface ({@code
 * org.sequeless.core.automation.DefaultActionExecutor}), and an adapter calls it, rather than the
 * other way around.
 *
 * <p>Every method takes the same two arguments: a {@link Scope} reconstructed from {@code
 * entry.payload()}'s own {@code tenantId}/{@code principalId} fields, and the {@code
 * ActionRequest}-kind {@link OutboxEntry} itself, whose {@code payload()} map is the sole source
 * of every field the method reads. That payload is a self-contained, JSON-compatible snapshot
 * frozen at transition-fire time by {@code org.sequeless.core.usecase.DefaultTransitionService},
 * so a retried Temporal activity (or an in-process relay retry) never needs to re-read mutable
 * object state to reproduce the same result. Every field common to all four action kinds, plus the
 * kind-specific fields each method below documents, is present in {@code entry.payload()}:
 *
 * <ul>
 *   <li>{@code objectId} ({@code String}) — the transitioning object's id; also required by {@link
 *       OutboxEntry} itself.
 *   <li>{@code tenantId} ({@code String}), {@code principalId} ({@code String}) — reconstruct the
 *       {@link Scope} the action is applied on behalf of.
 *   <li>{@code transitionName} ({@code String}), {@code actionIndex} ({@code Integer}, 0-based) —
 *       provenance, and (together with {@code objectId}) the seed {@link
 *       #applyCreateObject}'s idempotency derivation uses.
 *   <li>{@code typeIri} ({@code String}), {@code state} ({@code String}) — the transitioning
 *       object's type, and its <em>new</em> state (the transition's {@code toStateIri}) after the
 *       transition that produced this request.
 *   <li>{@code self} ({@code Map<String, Object>}) — a snapshot of the transitioning object's own
 *       properties, keyed by property <em>short name</em> (not IRI) — matching {@link
 *       org.sequeless.spi.expression.ExpressionContext#selfProperties()} exactly, so it decodes
 *       straight into an {@code ExpressionContext} with zero re-keying — each value tagged per
 *       {@code org.sequeless.core.statemachine.PayloadValueCodec}'s scheme.
 *   <li>{@code actionKind} ({@code String}) — one of {@code "SetProperty"}, {@code "CreateObject"},
 *       {@code "Webhook"}, or {@code "Log"}; not itself consulted by any one method below (each
 *       method already implies its own kind), but present on every payload.
 * </ul>
 */
public interface ActionExecutor {

    /**
     * Applies a {@code sq:SetProperty} action: sets a single property on the object named by
     * {@code entry.payload()}'s {@code objectId} to a target value, resolved either from a literal
     * {@code value} or evaluated from an {@code expression}, and commits the change only if it
     * actually differs from the property's current value (making a retried call naturally
     * idempotent). If the target object no longer exists (it may have been deleted since the
     * transition fired), this is a safe no-op — it never throws {@code ObjectNotFoundException},
     * so a retry always succeeds.
     *
     * <p>Kind-specific payload fields, in addition to those common to every action (see this
     * interface's own javadoc):
     *
     * <ul>
     *   <li>{@code property} ({@code String}) — the full IRI of the property to set.
     *   <li>{@code value} (tagged map) <em>or</em> {@code expression} ({@code String}) — exactly one
     *       is present: a literal target value (decoded via {@code PayloadValueCodec.fromPayload})
     *       or a JEXL expression evaluated against an {@code ExpressionContext} built from {@code
     *       self}/{@code state}/{@code principalId}.
     * </ul>
     *
     * @param scope the tenant and principal to apply the change on behalf of; must not be {@code
     *     null}
     * @param entry the {@code ActionRequest}-kind outbox entry describing the change; must not be
     *     {@code null}
     */
    void applySetProperty(Scope scope, OutboxEntry entry);

    /**
     * Applies a {@code sq:CreateObject} action: creates a new object of a given type with a set of
     * properties, each resolved either from a literal {@code value} or evaluated from an {@code
     * expression}. The new object's id is derived deterministically from {@code entry.id()} and
     * {@code actionIndex} rather than generated randomly, so a retried call is idempotent: if an
     * object already exists at that derived id, this method returns immediately without creating a
     * duplicate.
     *
     * <p>Kind-specific payload fields, in addition to those common to every action (see this
     * interface's own javadoc):
     *
     * <ul>
     *   <li>{@code createType} ({@code String}) — the full IRI of the type to create.
     *   <li>{@code createProperties} ({@code Map<String, Object>}) — keyed by full property IRI;
     *       each value is itself a nested map holding exactly one of {@code value} (tagged map,
     *       decoded via {@code PayloadValueCodec.fromPayload}) or {@code expression} ({@code
     *       String}, evaluated against an {@code ExpressionContext} built once from {@code
     *       self}/{@code state}/{@code principalId} and reused for every property).
     * </ul>
     *
     * @param scope the tenant and principal to create the object on behalf of; must not be {@code
     *     null}
     * @param entry the {@code ActionRequest}-kind outbox entry describing the object to create;
     *     must not be {@code null}
     */
    void applyCreateObject(Scope scope, OutboxEntry entry);

    /**
     * Resolves a {@code sq:Webhook} action's templated {@code url}/{@code method}/{@code body}
     * fields into a fully-rendered {@link ResolvedWebhookRequest}, without performing any HTTP I/O
     * itself — issuing the actual request is the calling automation adapter's job. {@code url} and
     * {@code body} (when present) are JXLT templates rendered through {@code
     * ExpressionPort#renderTemplate} against an {@code ExpressionContext} built from {@code
     * self}/{@code state}/{@code principalId}; {@code method} is used verbatim.
     *
     * <p>Kind-specific payload fields, in addition to those common to every action (see this
     * interface's own javadoc):
     *
     * <ul>
     *   <li>{@code url} ({@code String}) — a JXLT {@code ${expr}} template for the target URL.
     *   <li>{@code method} ({@code String}) — the literal HTTP method.
     *   <li>{@code body} ({@code String}, optional) — a JXLT {@code ${expr}} template for the
     *       request body, present iff the action defined one.
     * </ul>
     *
     * @param scope the tenant and principal to render the templates on behalf of; must not be
     *     {@code null}
     * @param entry the {@code ActionRequest}-kind outbox entry describing the webhook; must not be
     *     {@code null}
     * @return the fully-rendered request, ready for the caller to issue over HTTP
     */
    ResolvedWebhookRequest resolveWebhook(Scope scope, OutboxEntry entry);

    /**
     * Applies a {@code sq:Log} action: renders its templated {@code message} and logs it.
     *
     * <p>Kind-specific payload fields, in addition to those common to every action (see this
     * interface's own javadoc):
     *
     * <ul>
     *   <li>{@code message} ({@code String}) — a JXLT {@code ${expr}} template for the log line,
     *       rendered through {@code ExpressionPort#renderTemplate} against an {@code
     *       ExpressionContext} built from {@code self}/{@code state}/{@code principalId}.
     * </ul>
     *
     * @param scope the tenant and principal to render the template on behalf of; must not be {@code
     *     null}
     * @param entry the {@code ActionRequest}-kind outbox entry describing the log action; must not
     *     be {@code null}
     */
    void applyLog(Scope scope, OutboxEntry entry);
}
