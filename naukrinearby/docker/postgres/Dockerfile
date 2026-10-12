# PostgreSQL 16 with BOTH PostGIS (geospatial) and pgvector (embeddings) —
# NaukriNearby needs relational + geo + vector in one source-of-truth database.
#
# Base is the official pgvector image (postgres:16 on Debian bookworm) + PostGIS from PGDG.
# The previous base, postgis/postgis:16-3.x, is frozen on Debian bullseye, which apt.postgresql.org
# no longer serves (bullseye-pgdg returns 404), so `apt-get install postgresql-16-pgvector` failed.
FROM pgvector/pgvector:0.8.7-pg16-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-postgis-3 \
    && rm -rf /var/lib/apt/lists/*
