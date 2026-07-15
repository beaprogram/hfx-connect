-- Categories classify community resources (Food Assistance, Study Spaces, ...).
-- They are a small, admin-managed reference table, so a simple identity column
-- is used rather than a UUID (contrast with the larger, publicly-exposed
-- content entities such as resources, which will use UUID primary keys —
-- see ADR-005).
--
-- normalized_name enforces case-and-whitespace-insensitive uniqueness (see
-- ADR-005 for the exact normalization rule): "Food Assistance",
-- "food assistance", and "Food   Assistance" are all the same category.
-- slug is uniqued independently because two different display names can
-- normalize to the same slug (for example "Food Assistance" and
-- "Food, Assistance!" both slugify to "food-assistance") even though their
-- normalized_name values differ.
CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    slug VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT categories_normalized_name_key UNIQUE (normalized_name),
    CONSTRAINT categories_slug_key UNIQUE (slug)
);

-- Supports the common "list active categories ordered by name" query path
-- used by GET /api/v1/categories.
CREATE INDEX categories_active_name_idx ON categories (active, name);
