-- Index registries for the Query side (spec 13, P7): which sq:indexed properties have an expression
-- index, and which sq:searchable properties feed search_vector. Moved here from the Command migration
-- V2. CREATE TABLE IF NOT EXISTS, so a database that already ran V2 keeps its tables.

CREATE TABLE IF NOT EXISTS sq_index_registry (
    tenant_id     text NOT NULL,
    property_iri  text NOT NULL,
    index_name    text NOT NULL,
    created_at    timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, property_iri)
);

CREATE TABLE IF NOT EXISTS sq_searchable_property (
    tenant_id     text NOT NULL,
    property_iri  text NOT NULL,
    PRIMARY KEY (tenant_id, property_iri)
);
