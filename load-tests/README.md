# Reproducible load test

Use a disposable PostgreSQL database. Start the backend once so Flyway applies migrations, then seed it:

```powershell
$env:PGPASSWORD = '<local-password>'
psql -h localhost -U postgres -d sports_results_load -f .\load-tests\seed-100k.sql
```

The script creates one published Event with 100,000 Registration/Result pairs and prints the IDs needed by k6. Run the six required public-API scenarios together:

```powershell
$env:BASE_URL = 'http://127.0.0.1:8081'
$env:EVENT_ID = '<printed event_id>'
$env:RACE_ID = '<printed race_id>'
$env:CATEGORY_ID = '<printed female_category_id>'
$env:VUS = '12'
$env:DURATION = '30s'
k6 run .\load-tests\results.js
```

Custom `scenario_*` metrics report request rate and p50/p95/p99 latency for A–F. The default thresholds require an error rate below 1% and aggregate p95 below 1 second. Tune VUs only after recording the machine and database configuration.

The latest recorded local measurement and its tooling caveat are in [BASELINE.md](BASELINE.md). Treat the seed as disposable test data: never run it against the main `sports_results` database.
