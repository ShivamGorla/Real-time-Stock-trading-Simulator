# StockSim full-system QA and frontend optimization

Date: November 18, 2026 (IST). Presentation: November 19, 2026.

This is the original pre-commit QA snapshot. The later approval, final build and staging review are recorded in FINAL_REVIEW.md.

**Assessment: the core trading presentation candidate is ready for review. A complete presentation including working live AI is blocked by the rejected Groq credential. At that checkpoint, no commit, push or merge had been performed.**

## 1. Repository and architecture

- Repository: <repository>; origin: ShivamGorla/Real-time-Stock-trading-Simulator.
- Starting branch: feature/integrated-trading-dashboard.
- Verified starting and final HEAD: 6b58e7d41251ef082860f6def80089af339067a7.
- Review branch created from that commit: test/full-system-frontend-optimization.
- Initial working tree: only untracked PROJECT_DOCUMENTATION.txt. It remains untracked and untouched. Its final SHA-256 is 21E30C211A4F531B3D9857A58F72BEC545B160A0ED09B7550C0F348AF8ABEBAA; no before/after hash comparison is claimed. Its existing modification timestamp remains October 23, 2026.
- Nothing staged. No older frontend branch merged. Backend Java, pom.xml and application properties were not modified.

The application uses Spring MVC controllers and validated DTOs, Spring Security session authentication and cookie CSRF, JPA/MySQL repositories, and vanilla frontend scripts. Orders commit their PENDING intent before a separate settlement transaction. Wallet and holding ports/adapters participate in settlement, with trade and ledger audit rows. Top-ups use a user-scoped idempotency key and conditional payment transition in the same transaction as wallet credit. The assistant exposes six read-only tools, including the preview tool rather than a trade execution endpoint. Existing default auto-execution and assistant-disabled settings remain unchanged; execution was enabled only by the explicitly selected demo profile.

Frontend/controller/DTO contracts, transaction handling, security policy, reports and the existing test inventory were inspected before implementation. All production edits are frontend-only.

## 2. Executed checks

| Check | Result | Evidence |
|---|---|---|
| Baseline Maven clean test | PASS | 251 tests; 0 failures/errors/skipped |
| Interim Maven package | PASS | 251 tests and executable JAR |
| Final Maven clean test | PASS | 251 tests; 38 suites; 0 failures/errors/skipped |
| Final Maven package | PASS | 251 tests and repackaged executable JAR |
| JavaScript syntax | PASS | All 7 production JS files and both QA runners |
| git diff --check | PASS | No whitespace errors; Git only warns about LF/CRLF normalization |
| Edge 157.0.4341.60 fixture suite | PASS | 31 checks |
| Opera GX / Chromium 155.0.8059.85 fixture suite | PASS | 31 checks |
| Live packaged application + MySQL | PASS | 10 cases; real browser, HTTP and SQL |
| Responsive matrix | PASS | 1440, 1280, 768, 390 and 360px; both themes; Edge and Opera |
| axe WCAG A/AA | PASS | Zero violations in both tested themes in Edge and Opera |
| Live Groq answer and tool use | BLOCKED | LLM_AUTHENTICATION_ERROR; no successful provider call claimed |
| Firefox 159 / Playwright 1572 | BLOCKED | Windows browser launch: spawn UNKNOWN; no application cases ran |
| WebKit/Safari | NOT RUN | No installed runtime |

The original sandboxed Maven invocation stopped before tests because it could not write cache locks. Standard Maven clean/test and package goals were then executed successfully by invoking Maven's installed classworlds Java launcher directly with Java 21; pom.xml was unchanged. Baseline clean test took 1m46s, final clean test 54.579s, final package 42.466s. These are run durations, not a controlled backend speed comparison.

The 251 distinct Maven cases cover authentication, logout/security policy, CSRF and user isolation, profiles/passwords, initial wallet and ledger, top-up idempotency/concurrency/rollback, BUY/SELL settlement/precision/duplicates, holdings/WAC/realized and unrealized P&L/equity, reporting, assistant tools/isolation/provider errors/limits/conversation integrity, and fail-closed missing adapters. No backend test gap requiring a new Java test was found. New targeted browser regressions cover the verified frontend defects. See TEST_INVENTORY.md for all 251 individual cases and all browser cases, and maven-suites.json / maven-test-cases.json for machine-readable results.

Early live-run test assertions were corrected: search must wait for the intentional 120ms debounce, and password change correctly returns HTTP 204, not 200. Final results below are from the successful packaged-build rerun, rather than those preliminary harness failures.

## 3. Live MySQL and real browser journey

The environment was verified to target **stocksim_smoke_test**, never the default production schema. Its existing schema was checked, no public-ID backfill was required, and the demo was started on port 18080 with **hibernate.ddl-auto=validate**. No reset, drop, legacy migration or existing-user cleanup was performed. Only newly generated disposable QA accounts were used for financial and profile mutations. Test passwords were random and never printed or saved in results. A temporary encrypted environment handoff file was removed. The QA server was stopped after validation.

The packaged JAR served its bundled assets for the final run; no external static-directory override was used in that run.

Journey: registration → login → dashboard → INFY search/history → top-up → BUY → holdings/P&L → SELL → recent orders → all reports → assistant error handling → profile/password change → logout → relogin with changed test password → logout.

The final run used a newly generated disposable test account; its identifier is omitted. The test data remains in the disposable schema for inspection, including accounts from preliminary runs.

| SQL/API checkpoint | Verified result |
|---|---|
| Registration | Wallet ₹100,000.00; exactly one INITIAL_BALANCE ledger row |
| Browser top-up | ₹1,000 credited; one successful payment and TOP_UP ledger row |
| BUY 2 INFY | Server execution ₹1,922.08 each; wallet ₹97,155.84; quantity 2; WAC ₹1,922.08 |
| SELL 1 INFY | Server execution ₹1,922.08; wallet ₹99,077.92; quantity 1 |
| P&L at SELL checkpoint | Realized ₹0.00, unrealized ₹0.00; checked using actual execution/valuation values |
| Equity | API equity equals wallet plus current holdings market value |
| Duplicate clientOrderId | HTTP 409; no second settlement |
| Invalid quantity / missing CSRF | HTTP 400 / 403 |
| Insufficient funds / oversell | HTTP 422; balances/trades unchanged; committed PENDING order intents remain by backend design |
| Top-up replay | Same payment ID; one credit/ledger row |
| Concurrent same-key and distinct-key top-ups | Exactly ₹350 total credit; response statuses 200,200,409,200; original-key retry settled conflict safely |
| Declined simulated payment | FAILED; no balance change |
| Cross-user order access | HTTP 404; second user's orders empty and wallet still ₹100,000 |
| Unauthenticated protected/report requests | HTTP 401 |
| Trade pagination | size=1 returns one row with totalElements=2 |
| Final SQL | Wallet ₹99,527.92; one INFY; WAC 1922.080000; realized ₹0.00; 4 orders, 2 trades, 8 wallet ledger rows |
| Duplicate ledger references | 0 |

The four final orders are two executed trades and two deliberately failing PENDING settlement checks, not four executed trades. SQL amounts and portfolio arithmetic were compared to actual server prices, not client estimates. The simulator continued to generate prices, so unrealized valuation can change after a captured checkpoint.

All eight report links produced successful browser downloads. API checks verified HTTP 200, attachment filenames, CSV content type/nonempty bodies, and PDF magic bytes. Sizes for this run:

| Report | CSV bytes | PDF bytes |
|---|---:|---:|
| Profile | 211 | 1325 |
| Transactions | 107 | 1281 |
| Trading history | 145 | 1312 |
| Portfolio | 103 | 1291 |

Live AI returned an application-level error in an HTTP 200 envelope: **LLM_AUTHENTICATION_ERROR**. Its toolsUsed array was empty. Wallet balance/version, orders, trades, ledger, payments and holding quantity were identical before and after that chat. Successful live answers and live tool calls remain BLOCKED; mocked assistant unit/integration tests passed. No successful AI result is inferred from the HTTP status.

## 4. Frontend measurements

Controlled comparison: same one-stock HTTP fixture in Edge, initial load and five sequential manual refreshes. Instrumentation counts Intl.NumberFormat construction and MutationObserver records under market-workspace. These are actual operation counts, not Lighthouse scores or user-network latency measurements.

| Measurement | Before | After |
|---|---:|---:|
| Initial profile GET requests | 2 | 1 |
| Currency formatter constructions on initial load | 13 | 2 |
| Additional formatter constructions across five refreshes | 60 | 0 |
| Additional workspace mutation records across five refreshes | 200 | 90 |
| Initial workspace mutation records | 49 | 40 |
| Portfolio requests triggered by successful top-up | 0 | immediate event-driven refresh (verified by fixture and live SQL/browser case) |
| 360px baseline page scroll width | 376px | 360px |

The five-refresh mutation count fell 55%. The final production UI additionally shows account equity and realized/unrealized totals from the canonical portfolio DTO. Measurements are fixture-specific, do not establish a Lighthouse score, and do not establish a production latency improvement. Lighthouse was not run; axe and direct browser instrumentation supplied the accessibility/performance evidence.

## 5. Verified defects and fixes

| Priority | Finding | Fix and evidence |
|---|---|---|
| P1 | Order UUID existed only in memory; 422 discarded it despite a committed PENDING intent | Account-scoped sessionStorage and reconciliation; 422 retains UUID; reload, network failure, changed-input and same-ID retry cases pass |
| P1 | Unresolved top-up could be replaced by changing the amount; generic storage key crossed accounts | Block changed amount until resolved; account-scoped key and exact-key retry; account-isolation tests pass |
| P1 | Account form guard only disabled the button; duplicate programmatic submission could send twice | Explicit busy guard and safe promise handling; double-submit fixture sends one top-up |
| P1 | Top-up totals lagged; an older in-flight poll could hide the new trade balance | walletchange event and refreshSettledAccounts wait out old snapshots then fetch fresh totals; fixture race and live checks pass |
| P2 | CSS display overrode hidden buttons | Global hidden rule; reconciliation truly disappears after resolution |
| P2 | Mobile page overflow at 360px | Grid/input minimum widths, wrapping and internal table scrolling; all required widths pass in both browsers |
| P2 | Invalid definition-list nesting and inaccessible scroll regions | Valid nested dl groups; keyboard-focusable labelled tables and chat log; axe findings eliminated |
| P2 | User chat bubbles lacked dark-theme contrast | Use theme on-brand text and full-opacity role label; both-theme axe scans pass |
| P2 | Assistant initialization stole focus/scroll | Focus input only when explicitly starting a new conversation |
| P2 | Logout always navigated away even after failure; profile network errors silently left skeletons; login 5xx looked like wrong credentials | Explicit failure states, retained session UI on failed logout, honest account/login errors |
| P2 | Duplicate profile request, repeated formatter construction and unchanged table/list writes | Share currentUser promise; reuse formatter; skip unchanged renders/text; preserve selected stock and keyboard focus |
| P2 | Slow/superseded chart fetches wasted work; cached-page return stopped polling | Abort obsolete history requests while retaining request sequencing; restore polling on persisted pageshow; hidden/no-overlap tests pass |
| P2 | No bounded trading/assistant request wait; top-up timeout ended before reading response body | 15s trading/history and full top-up response timeout; 60s assistant timeout; never automatically replay uncertain financial POSTs |
| P2 | Mobile menu lacked Escape close/label reset | Escape closes and restores toggle focus; close updates accessible label |

Backend prices remain authoritative. CSRF and same-origin session behavior remain intact. Dynamic stock, user and AI text continues to use textContent/created DOM nodes; no new dynamic HTML injection or frontend secrets were added. BUY/SELL confirmation and cancellation, UUID version/variant, duplicate pending submission protection, account isolation and read-only chat are exercised by browser tests.

## 6. Accessibility, layout and console findings

Edge and Opera both passed all 31 fixture checks. The layout matrix covers both themes at 1440, 1280, 768, 390 and 360px, including long company names, extreme monetary values, existing holdings and empty-state checks. Wide tables scroll internally and are keyboard-focusable. Extreme monetary values wrap rather than cause page overflow.

Axe WCAG 2 A/AA and 2.1 AA checks found zero violations in the final light and dark fixture states in both browsers. This is automated coverage of those states, not a certification or a full assistive-technology audit. Live keyboard stock selection was verified. Screenshots cover the baseline mobile defect, both-theme desktop/mobile stress states in both browsers, and actual empty/holding account states.

There were **zero uncaught JavaScript exceptions** in both fixture browsers and the live journey. The live journey recorded **zero browser console errors**. Fixture console errors are expected evidence from explicitly injected network failures, HTTP 429 and HTTP 500. They are retained in frontend-*-results.json rather than suppressed. Edge and Opera share the Chromium family; independent-engine Firefox coverage is blocked by the host launch failure.

## 7. Exact changed files and evidence

Production files modified:

- src/main/resources/static/css/style.css
- src/main/resources/static/css/trading-dashboard.css
- src/main/resources/static/dashboard.html
- src/main/resources/static/js/app.js
- src/main/resources/static/js/assistant.js
- src/main/resources/static/js/auth.js
- src/main/resources/static/js/profile.js
- src/main/resources/static/js/trading-dashboard.js

The eight modified static assets were compared byte-for-byte by SHA-256 against their packaged JAR entries; all matched. A literal scan of 43 frontend/evidence text files found no occurrences of the configured database password or Groq key. This checks those known secrets, not every possible secret pattern.

No Java/backend/configuration/dependency files were modified. Added QA scripts and reports are under qa/:

- frontend-qa.cjs and live-qa.cjs: reproducible targeted regression runners.
- frontend-edge-results.json and frontend-opera-results.json: 31 cases each, matrices, accessibility findings and console logs.
- live-results.json: ten actual browser/MySQL cases, HTTP response log and AI BLOCKED outcome.
- performance.json, cross-browser-results.json, tool-versions.json.
- maven-suites.json, maven-test-cases.json, TEST_INVENTORY.md.
- README.md, QA_REPORT.md and FINAL_REVIEW.md. Temporary final-validation.json and git-status.txt stay local and are excluded from commits.
- screenshots/: captured states. logs/: raw Maven/browser/startup evidence; ignored by the existing *.log rule.

Tools used for browser checks: Playwright 1.65.1 and @axe-core/playwright 4.14.0, installed outside application dependencies. Presentation JAR: target/simulator-0.1.0.jar. Build output is ignored by Git and was not committed.

## 8. Remaining issues, ranked

1. **P1 — Live AI presentation blocker:** replace the rejected Groq credential in the local environment and rerun the live assistant checks. All six tools are covered by existing automated tests, but none were exercised successfully against the live provider in this run.
2. **P2 — Independent-engine coverage:** Firefox download completed, but Windows rejected its process launch with spawn UNKNOWN. WebKit/Safari were not run. Edge and Opera passed; do not present this as Firefox/Safari validation.
3. **P2 — Existing pending-order lifecycle:** failed settlement intentionally leaves a PENDING order. The frontend reconciles its ID and reports that state; it does not invent execution/cancellation APIs. No backend lifecycle redesign was made.
4. **P2 — Existing unbounded recent-orders API:** /api/orders returns all caller orders; the UI shows the latest eight. Long-history scaling remains a future backend/API task. Current-volume fixture/live checks passed.
5. **P3 — Storage-denied reload limitation:** retry intents survive reload when sessionStorage is available. The existing in-page fallback cannot survive a reload if browser storage is denied; storage-denial behavior was not part of this browser matrix.

No P0 defect was observed. Core authentication, profile, top-up, trading, portfolio, reports, themes and responsive behavior passed the executed checks. Keep the demo profile explicit for the presentation. Full-system readiness including live AI requires the credential correction and a successful live retest.

## 9. Review boundary

At the original review checkpoint, all application changes remained uncommitted and unstaged. PROJECT_DOCUMENTATION.txt was not edited, staged, removed or committed. No push or merge had occurred at that checkpoint. The historical status is shown below; the temporary git-status.txt stays local.

```text
## test/full-system-frontend-optimization
 M src/main/resources/static/css/style.css
 M src/main/resources/static/css/trading-dashboard.css
 M src/main/resources/static/dashboard.html
 M src/main/resources/static/js/app.js
 M src/main/resources/static/js/assistant.js
 M src/main/resources/static/js/auth.js
 M src/main/resources/static/js/profile.js
 M src/main/resources/static/js/trading-dashboard.js
?? PROJECT_DOCUMENTATION.txt
?? qa/
```
