-- Resources are real Halifax community services (food assistance, study
-- spaces, employment support, ...) classified by a category.
--
-- id is UUID (app-generated, via Hibernate) rather than the BIGINT identity
-- categories uses — resources are numerous, created over time, and may be
-- referenced in public URLs, unlike the small, curated category list. This
-- was already anticipated by ADR-005 when categories' ID type was decided.
--
-- category_id is BIGINT, matching categories.id exactly (categories was
-- deliberately given a numeric key, not a UUID — see ADR-005). Foreign keys
-- must match their referenced column's type; a UUID category_id would be a
-- type error against the already-applied, unmodifiable V2 migration.
--
-- slug reuses the exact same generation algorithm as categories (see
-- com.hfxconnect.common.text.SlugGenerator), so the format check below
-- matches categories' implicit format exactly: lowercase alphanumeric groups
-- separated by single hyphens, no leading/trailing/doubled hyphens.
CREATE TABLE resources (
    id UUID PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    address_line_1 VARCHAR(200) NOT NULL,
    address_line_2 VARCHAR(200),
    city VARCHAR(100) NOT NULL,
    province VARCHAR(2) NOT NULL,
    postal_code VARCHAR(7) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(180),
    website_url VARCHAR(500),
    cost_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    cost_details VARCHAR(500),
    eligibility VARCHAR(1000),
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT resources_slug_key UNIQUE (slug),

    CONSTRAINT resources_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT resources_description_not_blank CHECK (length(btrim(description)) > 0),
    CONSTRAINT resources_address_line_1_not_blank CHECK (length(btrim(address_line_1)) > 0),
    CONSTRAINT resources_city_not_blank CHECK (length(btrim(city)) > 0),

    CONSTRAINT resources_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),

    -- The 13 real Canadian province/territory codes, not just "two letters".
    CONSTRAINT resources_province_valid CHECK (province IN (
        'AB', 'BC', 'MB', 'NB', 'NL', 'NS', 'NT', 'NU', 'ON', 'PE', 'QC', 'SK', 'YT'
    )),

    -- Canonical normalized form "A1A 1A1": Canada Post excludes D, F, I, O, Q,
    -- and U from the first character.
    CONSTRAINT resources_postal_code_format CHECK (
        postal_code ~ '^[ABCEGHJKLMNPRSTVXY][0-9][A-Z] [0-9][A-Z][0-9]$'
    ),

    CONSTRAINT resources_cost_type_valid CHECK (cost_type IN ('FREE', 'LOW_COST', 'PAID', 'UNKNOWN')),
    CONSTRAINT resources_verification_status_valid CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED'))
);

-- Required: supports category-based resource access (service-layer
-- "active resources by category" queries, and the FK's own lookups when
-- checking ON DELETE RESTRICT against a category).
CREATE INDEX resources_category_id_idx ON resources (category_id);

-- Mirrors categories_active_name_idx: supports the "list active resources
-- ordered by name" query path the service layer uses for pagination.
CREATE INDEX resources_active_name_idx ON resources (active, name);
