-- Adds an optional geographic coordinate per resource. See ADR-012 for the
-- full design (geography vs geometry, SRID, native-query-only mapping
-- strategy, coordinate order).
--
-- location is GEOGRAPHY(POINT, 4326) — SRID 4326 (WGS 84) is the standard
-- latitude/longitude coordinate reference system every public coordinate
-- source (GPS, browsers, mapping APIs) already uses. geography (not
-- geometry) is deliberate: PostGIS calculates geography distances on the
-- actual spheroid directly in metres, with no separate planar-projection
-- step the application would otherwise have to choose and get right.
--
-- Nullable: most existing resources have no coordinates yet, and a
-- resource without one remains a fully valid, listable resource — it is
-- simply excluded from nearby search (see ResourceRepository.findNearby).
-- No default and no fabricated coordinates are ever written here.
--
-- No entity field maps this column (see ADR-012's "No Hibernate Spatial /
-- JTS Entity Mapping" decision) — every read and write goes through native
-- SQL on ResourceRepository, so Hibernate's ddl-auto=validate has nothing
-- to validate against this column specifically, and that is expected: it
-- only validates columns an entity actually maps, not every column a table
-- has.
ALTER TABLE resources
    ADD COLUMN location GEOGRAPHY(POINT, 4326);

-- GiST is the only index type PostGIS geography/geometry columns support
-- for spatial predicates (ST_DWithin, ST_Distance, bounding-box operators);
-- a B-tree index would be useless for spatial queries and is deliberately
-- not added alongside it. This index is what makes ST_DWithin's radius
-- search index-assisted rather than a full sequential scan once the
-- resource count grows.
CREATE INDEX idx_resources_location_gist ON resources USING GIST (location);
