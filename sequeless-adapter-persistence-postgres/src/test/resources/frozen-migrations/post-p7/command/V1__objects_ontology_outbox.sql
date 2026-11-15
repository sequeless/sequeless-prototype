-- Schema for the default ObjectStorePort adapter (plan.md §3): business objects with JSONB
-- properties, versioned ontology documents, and a transactional outbox. Run by this adapter's own
-- PostgresMigrations helper, scoped to classpath:db/migration/sequeless/postgres, never by Spring
-- Boot's Flyway autoconfiguration.

CREATE TABLE sq_object (
    id          uuid PRIMARY KEY,
    tenant_id   text NOT NULL,
    type_iri    text NOT NULL,
    version     bigint NOT NULL,
    state       text,
    props       jsonb NOT NULL,
    created_at  timestamptz NOT NULL,
    created_by  text NOT NULL,
    updated_at  timestamptz NOT NULL,
    updated_by  text NOT NULL,
    deleted_at  timestamptz
);

CREATE INDEX sq_object_props_gin_idx ON sq_object USING GIN (props jsonb_path_ops);

CREATE INDEX sq_object_tenant_type_created_idx
    ON sq_object (tenant_id, type_iri, created_at)
    WHERE deleted_at IS NULL;

CREATE TABLE sq_ontology_document (
    id          uuid PRIMARY KEY,
    tenant_id   text NOT NULL,
    version     int NOT NULL,
    format      text NOT NULL,
    content     text NOT NULL,
    active      boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL
);

CREATE UNIQUE INDEX sq_ontology_document_tenant_version_idx
    ON sq_ontology_document (tenant_id, version);

CREATE UNIQUE INDEX sq_ontology_document_tenant_active_idx
    ON sq_ontology_document (tenant_id)
    WHERE active;

CREATE TABLE sq_outbox (
    id           uuid PRIMARY KEY,
    tenant_id    text NOT NULL,
    object_id    uuid NOT NULL,
    kind         text NOT NULL,
    payload      jsonb NOT NULL,
    occurred_at  timestamptz NOT NULL,
    attempts     int NOT NULL DEFAULT 0
);

CREATE INDEX sq_outbox_tenant_idx ON sq_outbox (tenant_id);
