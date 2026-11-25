-- Command side of the search column (spec 13, P7). The Query migration set (postgres-query) owns the
-- index registries and the GIN index. This set keeps what writes sq_object: the search_vector column,
-- its two functions and its trigger.
--
-- Idempotent where V2 already ran on an existing database: ADD COLUMN IF NOT EXISTS, CREATE OR
-- REPLACE for the functions (their signatures are unchanged, which Postgres requires), and DROP
-- TRIGGER IF EXISTS before CREATE TRIGGER.

ALTER TABLE sq_object ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Extracts the text this property contributes to a row's search_vector, regardless of whether the
-- property is scalar-tagged ({"text": "..."}, for a cardinality-max-1 property) or list-tagged
-- ({"list": [{"text": "..."}, ...]}, for an unbounded-cardinality property such as ex:title or
-- ex:description -- sq:searchable carries no scalar-cardinality requirement, unlike sq:facet/
-- sq:indexed, which core's DefaultBusinessObjectService restricts to scalar properties only for
-- filter/sort/facet). Returns NULL (silently, via string_agg's null-skipping) when the property is
-- absent from props or neither shape matches.
CREATE OR REPLACE FUNCTION sq_extract_searchable_text(props jsonb, property_iri text) RETURNS text AS $$
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
-- It reads sq_searchable_property, which the Query migration set creates; PostgresMigrations applies
-- that set before any Command migration after this one. Mirrors, statement-for-statement, the backfill
-- UPDATE in PostgresObjectStore.backfill, which runs when a new searchable property is registered --
-- keep the two in sync if either changes (both call sq_extract_searchable_text so the per-property
-- extraction logic itself can't drift).
CREATE OR REPLACE FUNCTION sq_object_update_search_vector() RETURNS trigger AS $$
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

DROP TRIGGER IF EXISTS sq_object_search_vector_trigger ON sq_object;

CREATE TRIGGER sq_object_search_vector_trigger
    BEFORE INSERT OR UPDATE ON sq_object
    FOR EACH ROW EXECUTE FUNCTION sq_object_update_search_vector();
