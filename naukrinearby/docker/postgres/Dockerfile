# PostgreSQL 16 with BOTH PostGIS (geospatial) and pgvector (embeddings) —
# NaukriNearby needs relational + geo + vector in one source-of-truth database.
FROM postgis/postgis:16-3.4

RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-pgvector \
    && rm -rf /var/lib/apt/lists/*
