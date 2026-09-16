/**
 * Inbound REST adapter of sequeless-app.
 *
 * <p>{@link org.sequeless.app.rest.WhoAmIController} exposes {@code GET /whoami}, translating
 * between the SPI/core types {@code sequeless-core}'s {@link org.sequeless.core.api.WhoAmI} use
 * case works with and the wire format defined by {@link org.sequeless.app.rest.WhoAmIResponse}.
 * Together with {@link org.sequeless.app.config}, this is one of only two packages in the whole
 * reactor allowed to depend on {@code org.sequeless.core} — an ArchUnit rule in this module's test
 * sources enforces that no other package does.
 */
package org.sequeless.app.rest;
