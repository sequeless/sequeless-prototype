package org.sequeless.app.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.ontology.spi.OntologyDocumentStore;
import org.sequeless.spi.object.BusinessObject;
import org.sequeless.spi.object.ChangeSet;
import org.sequeless.spi.object.CommitResult;
import org.sequeless.spi.object.ObjectStorePort;
import org.sequeless.spi.object.Page;
import org.sequeless.spi.object.PageResult;
import org.sequeless.spi.object.TypeRef;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Decorates an {@link ObjectStorePort} so that every {@link #commit} runs in its own {@code
 * REQUIRES_NEW} transaction over the given transaction manager. Every read delegates unchanged.
 *
 * <p>Used only for the store the {@code DerivationRecomputer} bean commits through (see {@code
 * CoreConfiguration#derivationRecomputer}). It takes the transaction manager as a parameter and
 * does not know which DataSource that manager is over. The wiring passes a manager over the same
 * DataSource bean the claiming transaction uses, which is what makes {@code REQUIRES_NEW} suspend
 * the claim.
 */
final class RequiresNewCommitObjectStore implements ObjectStorePort {

    private final ObjectStorePort delegate;
    private final TransactionTemplate requiresNewTemplate;

    /**
     * @param delegate the store every read and commit is forwarded to; must not be {@code null}
     * @param transactionManager the manager each commit starts its own transaction on; must not be
     *     {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    RequiresNewCommitObjectStore(ObjectStorePort delegate, PlatformTransactionManager transactionManager) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.requiresNewTemplate = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.requiresNewTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<BusinessObject> find(Scope scope, ObjectId id) {
        return delegate.find(scope, id);
    }

    @Override
    public PageResult<BusinessObject> browse(Scope scope, Set<TypeRef> types, Page page) {
        return delegate.browse(scope, types, page);
    }

    @Override
    public Map<ObjectId, TypeRef> typesOf(Scope scope, Set<ObjectId> ids) {
        return delegate.typesOf(scope, ids);
    }

    /**
     * Commits {@code changeSet} in a new transaction. A {@link org.sequeless.spi.object.StaleObjectException}
     * thrown by the delegate rolls that transaction back and propagates unchanged, so the caller's
     * retry sees the same exception it would see from the undecorated store.
     */
    @Override
    public CommitResult commit(Scope scope, ChangeSet changeSet) {
        return requiresNewTemplate.execute(status -> delegate.commit(scope, changeSet));
    }

    @Override
    public OntologyDocumentStore ontologyDocuments() {
        return delegate.ontologyDocuments();
    }
}
