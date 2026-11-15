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

-- Extracts the text this property contributes to a row's search_vector, regardless of whether the
-- property is scalar-tagged ({"text": "..."}, for a cardinality-max-1 property) or list-tagged
-- ({"list": [{"text": "..."}, ...]}, for an unbounded-cardinality property such as ex:title or
-- ex:description -- sq:searchable carries no scalar-cardinality requirement, unlike sq:facet/
-- sq:indexed, which core's DefaultBusinessObjectService restricts to scalar properties only for
-- filter/sort/facet). Returns NULL (silently, via string_agg's null-skipping) when the property is
-- absent from props or neither shape matches.
CREATE FUNCTION sq_extract_searchable_text(props jsonb, property_iri text) RETURNS text AS $$
DECLARE
    node jsonb;
BEGIN
    node := props -> property_iri;
    IF node IS NULL THEN
        RETURN NULL;
    END IF;
    IF node ? 'text' THEN
        RETURN node ->> 'text';
    END IF;
    IF node ? 'list' THEN
        RETURN (
            SELECT string_agg(elem ->> 'text', ' ')
            FROM jsonb_array_elements(node -> 'list') AS elem
            WHERE elem ? 'text'
        );
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Recomputes search_vector from every property registered as searchable for the row's tenant.
-- Mirrors, statement-for-statement, the backfill UPDATE PostgresQueryStore.ensureIndexes runs when
-- a NEW property becomes searchable -- keep the two in sync if either changes (both call
-- sq_extract_searchable_text so the per-property extraction logic itself can't drift).
CREATE FUNCTION sq_object_update_search_vector() RETURNS trigger AS $$
DECLARE
    combined text;
BEGIN
    SELECT string_agg(sq_extract_searchable_text(NEW.props, sp.property_iri), ' ')
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
