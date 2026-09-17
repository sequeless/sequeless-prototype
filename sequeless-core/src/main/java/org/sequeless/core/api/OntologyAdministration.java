package org.sequeless.core.api;

import org.sequeless.core.AuthorizationException;
import org.sequeless.spi.Scope;
import org.sequeless.spi.authz.AuthorizationPort;
import org.sequeless.spi.authz.Operation;
import org.sequeless.spi.ontology.ImportMode;
import org.sequeless.spi.ontology.ImportReport;
import org.sequeless.spi.ontology.OntologyDocument;
import org.sequeless.spi.ontology.OntologyException;
import org.sequeless.spi.ontology.OntologyFormat;
import org.sequeless.spi.ontology.OntologyPort;

/**
 * Administrative export/import of the ontology as a whole, distinct from the read-only, per-type
 * access {@link MetaModelService} offers. Both operations here are administrative, not part of the
 * ordinary content lifecycle {@link BusinessObjectService} covers, which is why both authorize
 * {@link Operation#ADMIN} rather than {@link Operation#READ} or {@link Operation#EDIT}.
 *
 * @see org.sequeless.core.usecase.DefaultOntologyAdministration
 */
public interface OntologyAdministration {

    /**
     * Serialises the ontology currently loaded for {@code scope} as Turtle.
     *
     * <p>Authorizes {@link Operation#ADMIN} against {@link AuthorizationPort#EVERYTHING} before
     * delegating to {@link OntologyPort#export(Scope, OntologyFormat)} with {@link
     * OntologyFormat#TURTLE}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @return a non-null document containing the serialised ontology
     * @throws NullPointerException if {@code scope} is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws OntologyException if the currently loaded ontology is inconsistent
     */
    OntologyDocument export(Scope scope);

    /**
     * Replaces the ontology currently loaded for {@code scope} with the Turtle document {@code
     * content}.
     *
     * <p>Authorizes {@link Operation#ADMIN} against {@link AuthorizationPort#EVERYTHING}, wraps
     * {@code content} in an {@link OntologyDocument} tagged {@link OntologyFormat#TURTLE}, and
     * delegates to {@link OntologyPort#importDocument(Scope, OntologyDocument, ImportMode)} with
     * {@link ImportMode#REPLACE}.
     *
     * @param scope the tenant and principal the request is made on behalf of; must not be {@code
     *     null}
     * @param content the Turtle-serialised ontology document to import; must not be {@code null}
     * @return a non-null report describing the outcome of the import
     * @throws NullPointerException if either argument is {@code null}
     * @throws AuthorizationException if the authorization port denies the operation
     * @throws OntologyException if the resulting ontology is inconsistent
     */
    ImportReport importTurtle(Scope scope, String content);
}
