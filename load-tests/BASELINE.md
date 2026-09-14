# 100k local baseline

Measured on 2026-08-27 against a local PostgreSQL 18.3 database and the Spring Boot application with HikariCP `maximum-pool-size=12`. The reproducible seed created exactly 100,000 Registration/Result pairs in one Event and Race.

The official k6 script is `results.js`. This workstation could not download the k6 binary because the package/CDN endpoints were blocked, so the figures below are a supplemental warmed PowerShell/.NET HTTP run using the same A–F requests: 12 parallel workers, no think time, 20.64 seconds. They must not be presented as k6 results.

| Scenario | Requests | req/s | p50 ms | p95 ms | p99 ms | Errors |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| A — first page | 101 | 4.89 | 714.71 | 1208.71 | 1440.67 | 0 |
| B — Race + Female + Chip place | 96 | 4.65 | 808.97 | 1292.72 | 1428.38 | 0 |
| C — Category + Chip place | 92 | 4.46 | 666.21 | 1165.50 | 1456.46 | 0 |
| D — name search | 90 | 4.36 | 95.43 | 213.43 | 349.93 | 0 |
| E — bib lookup | 90 | 4.36 | 78.85 | 227.14 | 352.29 | 0 |
| F — catalog filters | 90 | 4.36 | 89.92 | 221.74 | 337.26 | 0 |

Aggregate: 559 requests, 27.09 req/s, 0% errors. A–C exceed the k6 script's provisional 1-second p95 target and should be profiled before making capacity claims.

`EXPLAIN ANALYZE` on the same data justified the indexes: partial-name lookup fell from about 246 ms (sequential scan) to 3.9 ms through GIN/trigram, category lookup from about 179 ms to 0.6 ms through a B-tree path, and exact bib lookup measured about 0.31 ms. No Redis, external search engine, or result cache was added.
