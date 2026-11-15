-- GIN index over the Command side's search_vector column (spec 13, P7). Moved here from the Command
-- migration V2. CREATE INDEX IF NOT EXISTS, so a database that already ran V2 keeps its index.

CREATE INDEX IF NOT EXISTS sq_object_search_vector_gin_idx ON sq_object USING GIN (search_vector);
