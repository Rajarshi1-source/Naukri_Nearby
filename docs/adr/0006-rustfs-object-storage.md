# ADR 0006: RustFS replaces MinIO for résumé object storage

## Status
Accepted 2026-10-12. Revised the same day after a fair re-evaluation (see "Corrections").

## Context
Résumés (PII under the DPDP Act) are stored in an S3-compatible bucket through `FileStorageService`
and the MinIO Java SDK 8.5.17. The service needs bucket exists/create, put/get/remove, **presigned
GET URLs**, and optional **SSE-S3** encryption at rest (`naukri.storage.encryption=sse`).

MinIO's community images are no longer usable: `minio/minio` was removed from Docker Hub in
Sept 2026, and the quay.io copy is frozen behind auth. This broke CI on `main` (run 35963388259,
`ContainerFetchException`/`NotFoundException` on the MinIO `GenericContainer`), as well as Compose.

**Workload profile, which decides the trade-offs:** one self-hosted Docker server shared with
Postgres, Elasticsearch, Redis and the JVM. Objects are PDF and image résumés of 0.3–10 MB (upload
limit 10 MB), at thousands to hundreds of thousands of objects, not billions. Uploads are proxied
through Spring Boot, which parses them immediately.

## Options
- **Ceph RGW**: rejected. It is a multi-daemon cluster (MON/OSD/RGW), heavy for a single server.
- **Garage**: rejected. No SSE-S3, keys and layout are bootstrapped through a CLI, and it is AGPL.
- **RustFS 1.0.1** vs **SeaweedFS 4.48**: both Apache-2.0, both single-container. Evaluated below.

## Method
Each store ran under Testcontainers with the **app's real code path** (`FileStorageService` +
MinIO SDK), in the same JVM, on the same host, interleaved R/S/R/S:

1. Contract: round-trip + erasure, presigned GET fetched over HTTP (plus a tampered signature), 9 MB
   résumé, SSE-S3 (`x-amz-server-side-encryption: AES256`), and the existing app storage tests.
2. Stress: 200 résumés (0.3–5 MB, 525 MB total), 16 in flight on virtual threads. Each is uploaded,
   downloaded and byte-compared, then fetched again through its signed URL.
3. Resources: exact cgroup v2 `memory.peak` (includes page cache), the maximum `anon` memory
   (process heap, polled every 0.3 s), and total `cpu.stat` CPU time for the store's container.
4. Durability: upload a 3 MB object to a named volume, restart the container, and verify the SHA-256.

## Results

| | RustFS 1.0.1 | SeaweedFS 4.48 |
|---|---|---|
| Contract checks | pass (a tampered signature gets **400**, not S3's 403; still rejected) | pass (403) |
| SSE-S3 | pass (`RUSTFS_SSE_S3_MASTER_KEY`) | pass (`WEED_S3_SSE_KEK`) |
| Stress, 5 rounds | **0 errors** in every round | **0 errors** in every round |
| Throughput, MB/s | 234, 282, 220, 299, 354 | 204, 244, 205, 249, 281 |
| Peak memory, incl. cache | 835, 953 MiB | 1,454, 1,354 MiB |
| Peak anonymous memory | **465 MiB** | **961 MiB** |
| CPU per burst | 24.7, 13.8 s | 32.6, 25.6 s |
| Restart persistence | pass | pass |
| Idle memory | ~87–97 MiB | ~64–116 MiB |
| Ops fit | MinIO-compatible env vars, health paths and port 9000 | env creds (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`), port 8333, `/healthz` |
| Image | 405 MB | 724 MB |
| Maturity | 1.0 GA 2026-09-16 (< 1 month) | ~12 years, very wide production use |

## Assessing the external claims we were given
| Claim | Verdict for this project |
|---|---|
| "RustFS is alpha/beta, wait for 1.0" | **Outdated.** 1.0.0 GA was 2026-09-16, and 1.0.1 shipped 2026-10-03. |
| "RustFS crashes under concurrency" | The source (rustfs discussion #1500) tested **1.0.0-alpha.79** on a **4-node cluster at 512 threads** in Jan 2026. Maintainers reported a fix on 2026-01-16. Our single-node test at the app's realistic concurrency had 0 errors in 5 rounds. Concurrency far above ours is **not** covered by this evidence. |
| "RustFS memory spikes, SeaweedFS scales flat" | **Contradicted for this workload.** Measured exactly, SeaweedFS used ~2× the process memory and more CPU. |
| "SeaweedFS ~800 MB idle vs RustFS ~345 MB" | Unsourced. Both idled near 100 MiB here. |
| "SeaweedFS is built for lots of small files" | True, but **irrelevant**: résumés are 0.3–10 MB and few in number. |
| "SeaweedFS needs master + volume + filer + S3 gateway" | Four *roles*, but `weed server -s3` runs them in **one container**. Not a real cost. |
| "SeaweedFS works better with Spring and the AWS SDK" | Not supported: both passed the same SDK contract. Our code is plain S3. |
| "SeaweedFS is far more mature" | **True, and it is the strongest argument against RustFS.** It is why the mitigations below exist. |

## Decision
Keep **RustFS 1.0.1** (pinned) in Compose and Testcontainers. On this project's single-server
deployment and workload, it matched SeaweedFS on correctness and durability and used about half the
memory and CPU. That matters on a box shared with Elasticsearch and Postgres. It is also the closest
operational fit to the MinIO setup it replaces.

## Corrections to the first version of this ADR
- It stated "SeaweedFS fails SSE-S3". **Wrong**: SeaweedFS was started without its KEK
  (`WEED_S3_SSE_KEK`), while RustFS and MinIO were given keys. With the KEK set, SSE-S3 passes.
- It cited idle memory as a differentiator. That was measured with a coarse `docker stats` sampler,
  and a string-compare bug in the summary script made the first load-memory figures invalid. The
  numbers above come from cgroup `memory.peak`/`anon`.

## Consequences and risk controls
- **Maturity risk is accepted but bounded.** The app speaks only standard S3, so the store is
  swappable. `ObjectStorageIntegrationTests` (erasure, signed URLs, 9 MB upload, SSE-S3) gates every
  build, and SeaweedFS has already passed the same contract. **Fallback recipe:** image
  `chrislusf/seaweedfs:4.48`, command `server -dir=/data -s3`, env `AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY` and `WEED_S3_SSE_KEK` (64 hex), port 8333, health `/healthz`. Point
  `MINIO_ENDPOINT` at it. No code change is needed.
- Pin exact RustFS versions and upgrade deliberately, re-running the contract suite each time.
- **Revisit the decision if** upload concurrency grows well beyond ~16 in flight, the store moves to
  multi-node or cross-site replication (SeaweedFS's strength), or RustFS shows instability in our
  own monitoring.
- **Production note:** a single-node, single-disk store of either kind has no redundancy. For
  production PII, prefer a managed S3 service in an Indian region (DPDP data residency), or add
  scheduled off-host backups (`mc mirror` / `aws s3 sync`) of the `rustfs_data` volume.
- Existing local `minio_data` volumes are not migrated. This is dev data: re-upload it, or copy it
  with any S3 client.
