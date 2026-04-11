# BYSEL Sprint Board (90 Days)

Date: 2026-03-05
Branch: android-build-fixes
Source: docs/COMPETITIVE_ROADMAP_90_DAYS.md

## How To Use

1. Import these items as Jira tickets (Epic -> Story -> Task).
2. Track status as `todo`, `in_progress`, `blocked`, `done`.
3. Keep weekly KPI snapshot in each sprint review.

## Execution Pack

1. Roadmap: `docs/COMPETITIVE_ROADMAP_90_DAYS.md`
2. Sprint board: `docs/SPRINT_BOARD_90_DAYS.md`
3. Jira CSV import: `docs/JIRA_IMPORT_90_DAYS.csv`
4. Sprint 1 kickoff plan: `docs/SPRINT_1_KICKOFF_PLAYBOOK.md`

## Live P0 Status Tracker (As Of 2026-03-15)

Status legend: `todo`, `in_progress`, `blocked`, `done`

| ID | Title | Owner | Priority | Status | Sprint | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| S1-001 | Implement idempotent order placement | Backend | P0 | in_progress | Sprint 1 | Critical for retry safety |
| S1-002 | Build deterministic order lifecycle state machine | Backend | P0 | in_progress | Sprint 1 | Required for OMS consistency |
| S1-003 | Add reconnect-safe websocket stream | Backend + Android | P0 | in_progress | Sprint 1 | Directly impacts quote/order freshness |
| S1-004 | Build SLO dashboard for orders and quotes | DevOps | P0 | todo | Sprint 1 | Needed before scale push |
| S1-005 | Add release gate checks for crash and P0 bugs | QA | P0 | todo | Sprint 1 | Release quality control |
| S2-001 | Show pre-trade margin and charge breakdown | Android + Backend | P0 | todo | Sprint 2 | Core execution transparency |
| S2-002 | Map all rejection reasons to user-friendly actions | Android | P0 | todo | Sprint 2 | Reduce failed-order confusion |
| S2-003 | Add trace IDs from mobile to backend logs | Backend | P0 | todo | Sprint 2 | Faster support resolution |
| S3-001 | Dedicated Futures discover -> ticket -> order flow | Android + Backend | P0 | todo | Sprint 3 | Product-depth parity |
| S3-002 | Strategy payoff, Greeks, and risk warnings | Android | P0 | todo | Sprint 3 | Options safety and confidence |
| S3-003 | Multi-leg payload validation and guardrails | Backend | P0 | todo | Sprint 3 | Risk containment |

### Recently Delivered (Non-Board Fast Fixes)

1. v2.6.60: Improved input contrast and added refresh actions in More -> Mutual Funds, IPO, ETFs.
2. v2.6.58-v2.6.60: AI prompt quality and stock-name handling improvements.

## Next 28-Day P0 Ownership Board (2026-03-16 To 2026-04-12)

### Week 1 (Day 1-7): Reliability Core (Sprint 1)

| Day | Date | Backend | Android | QA + DevOps | Daily Exit Gate |
| --- | --- | --- | --- | --- | --- |
| 1 | 2026-03-16 | Finalize idempotency key contract | Reconnect UX event-state mapping | KPI baseline capture | Baseline metrics frozen |
| 2 | 2026-03-17 | Idempotent order create/update path | Client retry token propagation | Retry regression suite prep | No duplicate order in unit tests |
| 3 | 2026-03-18 | State transition validator middleware | Order state sync UI hooks | Invalid-transition negative tests | Invalid transitions rejected deterministically |
| 4 | 2026-03-19 | WS sequence tracking + gap detection | Reconnect + backfill handler | Disconnect chaos test setup | Gap recovery path implemented |
| 5 | 2026-03-20 | Replay endpoint for missed events | Resume stream without duplicate rows | Real-time consistency assertions | Reconnect test pass >= 95 percent |
| 6 | 2026-03-21 | Order/quote SLI emitters | Surface stream health in UI | SLO dashboard and alerts wired | p95 panels visible in dashboard |
| 7 | 2026-03-22 | Patch remaining P0 reliability defects | Market-hour reconnect UX polish | Release gate scripts draft | Sprint 1 candidate ready |

### Week 2 (Day 8-14): Transparency Hardening (Sprint 2)

| Day | Date | Backend | Android | QA + DevOps | Daily Exit Gate |
| --- | --- | --- | --- | --- | --- |
| 8 | 2026-03-23 | Margin estimate API contract freeze | Ticket screen margin layout | API contract validation tests | Contract approved by all leads |
| 9 | 2026-03-24 | Charges breakdown engine + endpoint | Charges component in ticket | Snapshot tests for ticket totals | Pre-trade amounts visible in app |
| 10 | 2026-03-25 | Rejection taxonomy map (RMS/OMS) | Error copy/action mapping framework | Rejection fixture matrix | Top rejection classes mapped |
| 11 | 2026-03-26 | Trace ID injection in API pipeline | Propagate trace ID in request headers | Log correlation drill | End-to-end trace visible |
| 12 | 2026-03-27 | Rejection fallback and unknown handling | Recovery CTAs for each rejection | UX and localization QA | 100 percent mapped UI messages |
| 13 | 2026-03-28 | Support lookup endpoint by trace ID | Error details panel instrumentation | Support SOP dry run | Failed order trace <= 2 minutes |
| 14 | 2026-03-29 | Close Sprint 2 P0 defects | Final polish + analytics events | Release gate execution | Sprint 2 release candidate ready |

### Week 3 (Day 15-21): Futures MVP Build (Sprint 3 Start)

| Day | Date | Backend | Android | QA + DevOps | Daily Exit Gate |
| --- | --- | --- | --- | --- | --- |
| 15 | 2026-03-30 | Futures instrument API + metadata | Futures discover list screen | Futures contract test data prep | Discover flow usable |
| 16 | 2026-03-31 | Futures ticket validation rules | Futures order ticket UI | Validation negative-path tests | Ticket validations aligned |
| 17 | 2026-04-01 | Futures order placement endpoint | Submit + confirm flow | Order lifecycle test suite | First E2E futures order success |
| 18 | 2026-04-02 | Futures positions and PnL endpoint | Positions and PnL screen binding | Position reconciliation checks | Position updates accurate |
| 19 | 2026-04-03 | Realtime futures quote stream hook | Live PnL refresh handling | Stream stability tests | Live quote and PnL updates pass |
| 20 | 2026-04-04 | Risk guard checks for futures orders | Risk warning surfaces in ticket | Risk scenario matrix | High-risk orders blocked with reason |
| 21 | 2026-04-05 | Futures defect sweep + stabilization | UX polish and fallback states | UAT + canary readiness | Sprint 3 MVP candidate ready |

### Week 4 (Day 22-28): Futures Hardening + Options Safety Kickoff

| Day | Date | Backend | Android | QA + DevOps | Daily Exit Gate |
| --- | --- | --- | --- | --- | --- |
| 22 | 2026-04-06 | Multi-leg payload validator baseline | Options strategy input shell | Multi-leg contract tests | Validator scaffold merged |
| 23 | 2026-04-07 | Greeks/chain API alignment | Payoff and Greeks visualization draft | Calculation fixture tests | Risk math outputs match fixtures |
| 24 | 2026-04-08 | Strategy warning rules engine | Warning UX with hard-stop behavior | Unsafe strategy scenario tests | High-risk combos blocked |
| 25 | 2026-04-09 | Improve futures stream resilience | Futures latency and staleness indicators | Soak tests (market open simulation) | Stale update rate under threshold |
| 26 | 2026-04-10 | Support hooks for futures + options errors | Error recovery actions for derivatives | Drill for incident response | MTTR drill target met |
| 27 | 2026-04-11 | Pre-release defect closure | Final UI polish and accessibility pass | Full regression + release gate | No unresolved P0 defects |
| 28 | 2026-04-12 | Go/no-go evidence package | Release analytics checklist | Production readiness review | Derivatives release decision signed |

## Weekly Non-Negotiable Gates (Competitive Parity)

1. Crash-free sessions >= 99.8 percent.
2. Order success rate >= 99.5 percent during market hours.
3. p95 quote latency <= 300 ms for tracked symbols.
4. Failed-order traceability <= 2 minutes via trace ID.
5. Zero unresolved P0 defects at release cut.

## Release Calendar (Proposed)

| Sprint | Start Date | End Date | Production Release Window | Primary Owners | Core Milestone |
| --- | --- | --- | --- | --- | --- |
| Sprint 1 | 2026-03-09 | 2026-03-22 | 2026-03-20 to 2026-03-22 | Backend, Android, DevOps, QA | Idempotent order flow and reconnect-safe streaming |
| Sprint 2 | 2026-03-23 | 2026-04-05 | 2026-04-03 to 2026-04-05 | Android, Backend, QA | Margin and charges transparency with rejection mapping |
| Sprint 3 | 2026-04-06 | 2026-04-19 | 2026-04-17 to 2026-04-19 | Android, Backend | Dedicated Futures and options risk depth |
| Sprint 4 | 2026-04-20 | 2026-05-03 | 2026-05-01 to 2026-05-03 | Android, Backend, Compliance | KYC hardening and sell authorization controls |
| Sprint 5 | 2026-05-04 | 2026-05-17 | 2026-05-15 to 2026-05-17 | Android, Backend, QA | SGB module and reporting center |
| Sprint 6 | 2026-05-18 | 2026-05-31 | 2026-05-29 to 2026-05-31 | Android, Data, DevOps, QA | Performance hardening and growth features |

## Sprint 1 (Weeks 1-2): Reliability Baseline

### Epic S1-E1: Order Flow Reliability

- ID: S1-001
  Type: Story
  Title: Implement idempotent order placement
  Owner: Backend
  Points: 8
  Priority: P0
  Acceptance:
  - Repeated client retries do not create duplicate orders.
  - API returns same order reference for same idempotency key.

- ID: S1-002
  Type: Story
  Title: Build deterministic order lifecycle state machine
  Owner: Backend
  Points: 8
  Priority: P0
  Acceptance:
  - Allowed transitions documented and enforced.
  - Invalid transitions are rejected with clear error code.

- ID: S1-003
  Type: Story
  Title: Add reconnect-safe websocket stream
  Owner: Backend + Android
  Points: 8
  Priority: P0
  Acceptance:
  - Stream resumes after disconnect with no duplicate events.
  - Sequence gap recovery is validated in tests.

### Epic S1-E2: Observability and Release Gates

- ID: S1-004
  Type: Story
  Title: Build SLO dashboard for orders and quotes
  Owner: DevOps
  Points: 5
  Priority: P0
  Acceptance:
  - Dashboard exposes p95 latency, error rate, and success rate.
  - Alert thresholds configured for critical regressions.

- ID: S1-005
  Type: Task
  Title: Add release gate checks for crash and P0 bugs
  Owner: QA
  Points: 3
  Priority: P0
  Acceptance:
  - Release blocked if crash-free < target.
  - Release blocked if unresolved P0 defects exist.

## Sprint 2 (Weeks 3-4): Execution Transparency

### Epic S2-E1: Ticket and Validation Hardening

- ID: S2-001
  Type: Story
  Title: Show pre-trade margin and charge breakdown
  Owner: Android + Backend
  Points: 8
  Priority: P0
  Acceptance:
  - Margin and charges are visible before submit.
  - Final execution charges reconcile with tradebook values.

- ID: S2-002
  Type: Story
  Title: Map all rejection reasons to user-friendly actions
  Owner: Android
  Points: 5
  Priority: P0
  Acceptance:
  - All RMS/OMS errors have deterministic UI copy and action.

- ID: S2-003
  Type: Task
  Title: Add trace IDs from mobile to backend logs
  Owner: Backend
  Points: 3
  Priority: P0
  Acceptance:
  - Support can trace any failed order by trace ID within 2 minutes.

## Sprint 3 (Weeks 5-6): F&O Product Depth

### Epic S3-E1: Dedicated Futures Flow

- ID: S3-001
  Type: Story
  Title: Implement dedicated Futures discover -> ticket -> order flow
  Owner: Android + Backend
  Points: 13
  Priority: P0
  Acceptance:
  - Futures contracts searchable and tradable end-to-end.
  - Positions and PnL update in real time.

### Epic S3-E2: Options Strategy Safety

- ID: S3-002
  Type: Story
  Title: Add strategy payoff, Greeks, and risk warnings
  Owner: Android
  Points: 8
  Priority: P0
  Acceptance:
  - Multi-leg preview displays payoff and net Greeks.
  - Hard warning shown for high-risk combinations.

- ID: S3-003
  Type: Task
  Title: Validate multi-leg payload and guardrails on backend
  Owner: Backend
  Points: 5
  Priority: P0
  Acceptance:
  - Invalid strategy legs rejected with actionable errors.

## Sprint 4 (Weeks 7-8): Compliance and Security

### Epic S4-E1: Onboarding and Sell Authorization

- ID: S4-001
  Type: Story
  Title: Harden KYC and verification flow
  Owner: Backend + Android
  Points: 8
  Priority: P0
  Acceptance:
  - Drop-off rate reduced at verification steps.
  - Retry and recovery flow available for failed checks.

- ID: S4-002
  Type: Story
  Title: Implement eDIS/TPIN sell authorization UX
  Owner: Android + Backend
  Points: 8
  Priority: P0
  Acceptance:
  - Sell authorization status is clear and recoverable.

### Epic S4-E2: App Security Controls

- ID: S4-003
  Type: Story
  Title: Add session management and suspicious-login controls
  Owner: Backend
  Points: 5
  Priority: P0
  Acceptance:
  - User can view active sessions and revoke devices.

- ID: S4-004
  Type: Task
  Title: Add certificate pinning and root detection checks
  Owner: Android
  Points: 5
  Priority: P0
  Acceptance:
  - Security checks active in release builds with telemetry.

## Sprint 5 (Weeks 9-10): Wealth and Reports

### Epic S5-E1: SGB Module

- ID: S5-001
  Type: Story
  Title: Build SGB catalog and application flow
  Owner: Android + Backend
  Points: 13
  Priority: P0
  Acceptance:
  - User can discover, apply, and track SGB status.

### Epic S5-E2: Reports and Statements

- ID: S5-002
  Type: Story
  Title: Add tax PnL summary and statement center
  Owner: Backend + Android
  Points: 8
  Priority: P1
  Acceptance:
  - Downloadable tax and statement artifacts available in app.

- ID: S5-003
  Type: Task
  Title: Add ledger and tradebook reconciliation checks
  Owner: QA + Backend
  Points: 5
  Priority: P1
  Acceptance:
  - Reconciliation mismatch alerts generated automatically.

## Sprint 6 (Weeks 11-12): Scale and Growth

### Epic S6-E1: Performance and Device Quality

- ID: S6-001
  Type: Story
  Title: Optimize startup and render for low-end devices
  Owner: Android
  Points: 8
  Priority: P0
  Acceptance:
  - Startup and frame metrics meet target budgets.

- ID: S6-002
  Type: Task
  Title: Run opening-hour chaos and load tests
  Owner: QA + DevOps
  Points: 5
  Priority: P0
  Acceptance:
  - Platform survives defined volatility test scenarios.

### Epic S6-E2: Retention Features

- ID: S6-003
  Type: Story
  Title: Add alert intelligence and watchlist scanners
  Owner: Android + Data
  Points: 8
  Priority: P1
  Acceptance:
  - D7 and D30 engagement lift measured against baseline.

## KPI Tracking Template (Per Sprint Review)

- Crash-free sessions:
- Order success rate:
- p95 order latency:
- p95 quote latency:
- Onboarding completion:
- D1/D7/D30 retention:
- Support tickets per 1,000 users:
- MTTD/MTTR:

## Definition of Done (All P0 Stories)

- Unit tests and integration tests pass.
- Critical regression suite passes.
- Monitoring and alerting coverage added.
- Product and compliance sign-off recorded.
