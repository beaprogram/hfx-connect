-- Enables PostGIS as a tracked, versioned migration rather than relying on the
-- local Docker image's implicit initialization, so the extension is guaranteed
-- present in every environment (local, CI, and the managed production database
-- introduced in Milestone 12) that Flyway migrates.
CREATE EXTENSION IF NOT EXISTS postgis;
