/**
 * The plug-in vocabulary for {@code sq:Plugin}-derived properties: a named, {@code ServiceLoader}-
 * discovered computation that runs in place of a stored value on read.
 *
 * <p>Like every package beneath {@code org.sequeless.spi}, this package imports nothing outside the
 * JDK ({@code java.*}) except sibling {@code org.sequeless.spi.*} types: {@link
 * org.sequeless.spi.Scope} from its parent package, {@link org.sequeless.spi.meta.MetaModelSnapshot}
 * from {@code org.sequeless.spi.meta}, {@link org.sequeless.spi.query.QueryPort} from {@code
 * org.sequeless.spi.query}, and {@link org.sequeless.spi.object.BusinessObject}, {@link
 * org.sequeless.spi.object.ObjectId}, and {@link org.sequeless.spi.object.Value} from {@code
 * org.sequeless.spi.object}. {@link org.sequeless.spi.derivation.DerivationPlugin} is the {@link
 * java.util.ServiceLoader} service type itself — unlike {@link
 * org.sequeless.spi.AdapterDescriptorProvider}, it needs no provider-indirection wrapper, since an
 * implementing class (not a record) can be instantiated reflectively via a no-arg constructor.
 * {@link org.sequeless.spi.derivation.DerivationContext} is the request-scoped state {@code
 * ServiceLoader}'s no-arg instantiation cannot otherwise supply: the tenant/principal {@link
 * org.sequeless.spi.Scope}, the {@link org.sequeless.spi.meta.MetaModelSnapshot} in effect, and
 * {@link org.sequeless.spi.query.QueryPort} access for a plug-in that needs to look up related
 * objects itself.
 */
package org.sequeless.spi.derivation;
