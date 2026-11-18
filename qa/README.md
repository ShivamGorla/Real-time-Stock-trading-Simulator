# StockSim QA runners

Run from the repository root on Java 21, Maven and Windows with Edge.

## Java

`mvn clean test` and `mvn package` use H2 for tests. The suite contains 251 tests in 38 classes. Do not run legacy wallet migrations.

## Browser tools (outside application dependencies)

Install the recorded versions in a temporary tools directory:

`npm.cmd install --prefix "$env:TEMP\stocksim-qa-tools" playwright@1.65.1 @axe-core/playwright@4.14.0`

Set `QA_NODE_MODULES` to that directory's `node_modules` if your temporary directory differs. There are no new production dependencies.

- `node qa/frontend-qa.cjs`: Edge against explicit HTTP fixtures, including offline/error injection, responsive layout and axe checks. This does not validate Spring or MySQL.
- `node qa/frontend-qa.cjs --firefox`: same fixtures with Firefox, after `node <tools>/node_modules/playwright/cli.js install firefox`. Firefox launch was blocked on this host.
- `node qa/frontend-qa.cjs --opera`: installed Opera GX coverage; all 31 fixture checks passed on this host.
- `node qa/live-qa.cjs`: actual browser/server/SQL journey. Requires a running demo-profile server on localhost:18080, the authorized `DB_URL` targeting exactly `stocksim_smoke_test`, `DB_USERNAME`, and `DB_PASSWORD`. `QA_BASE_URL`, `QA_MYSQL` and `QA_BROWSER` optionally select tools/port. Never print credential values.

For live validation, start the packaged application with `--spring.profiles.active=demo --server.port=18080 --spring.jpa.hibernate.ddl-auto=validate`. Normal application defaults remain unchanged. The live runner creates disposable test users, payments and orders, including deliberately PENDING failed settlements. It does not reset, drop or delete existing data. Keep that test data for inspection. It uses randomly generated test passwords which are never written to results.

The live runner's exit code covers its ten application cases. Inspect `live-results.json.ai.status` separately: the AI success gate is BLOCKED when provider credentials fail, even if the other application checks pass.

Results and screenshots are written under `qa/`. Raw logs under `qa/logs/` are ignored by the repository's existing *.log rule. See `QA_REPORT.md` and `TEST_INVENTORY.md` for scope, exact outcomes and remaining limitations.

Only reviewed text summaries and runners are committed. Raw logs, screenshots, final-validation.json and git-status.txt remain local under qa/. See FINAL_REVIEW.md for the approval-time build, privacy and optional P&L checks.

Optional nonzero realized P&L: `node qa/nonzero-pnl-qa.cjs` uses the same disposable database/demo server requirements. It buys one INFY share, waits up to 60 seconds for the existing simulated price engine, sells through the actual frontend, and compares the nonzero value in the UI, portfolio API and MySQL. It never edits prices or trading calculations. Its summary omits test-account identifiers.
