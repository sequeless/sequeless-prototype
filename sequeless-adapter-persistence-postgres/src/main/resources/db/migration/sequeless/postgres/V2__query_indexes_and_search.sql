-- Adds hint-driven expression indexes, full-text search, and their bookkeeping registries on top
-- of V1's sq_object table (plan.md "PostgreSQL adapter design"). Discovered by this adapter's own
-- PostgresMigrations helper against classpath:db/migration/sequeless/postgres, same as V1.

CREATE TABLE sq_index_registry (
    tenant_id     text NOT NULL,
    property_iri  text NOT NULL,
    index_name    text NOT NULL,
    created_at    timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, property_iri)
);

CREATE TABLE sq_searchable_property (
    tenant_id     text NOT NULL,
    property_iri  text NOT NULL,
    PRIMARY KEY (tenant_id, property_iri)
);

ALTER TABLE sq_object ADD COLUMN search_vector tsvector;

-- Recomputes search_vector from every property registered as searchable for the row's tenant,
-- pulling each property's 'text'-tagged value out of props. Mirrors, statement-for-statement, the
-- backfill UPDATE PostgresQueryStore.ensureIndexes runs when a NEW property becomes searchable --
-- keep the two in sync if either changes.
CREATE FUNCTION sq_object_update_search_vector() RETURNS trigger AS $$
DECLARE
    combined text;
BEGIN
    SELECT string_agg(NEW.props -> sp.property_iri ->> 'text', ' ')
    INTO combined
    FROM sq_searchable_property sp
    WHERE sp.tenant_id = NEW.tenant_id;

    NEW.search_vector := to_tsvector('simple', COALESCE(combined, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sq_object_search_vector_trigger
    BEFORE INSERT OR UPDATE ON sq_object
    FOR EACH ROW EXECUTE FUNCTION sq_object_update_search_vector();

CREATE INDEX sq_object_search_vector_gin_idx ON sq_object USING GIN (search_vector);
