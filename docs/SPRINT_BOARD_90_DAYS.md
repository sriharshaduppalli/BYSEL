# BYSEL Sprint Board (90 Days)

Date: 2026-03-19
Branch: main
Source: docs/COMPETITIVE_ROADMAP_90_DAYS.md

## How To Use

1. Import these items as Jira tickets (Epic -> Story -> Task).
2. Track status as `todo`, `in_progress`, `blocked`.
3. Keep weekly KPI snapshot in each sprint review.

## Execution Pack

1. Roadmap: `docs/COMPETITIVE_ROADMAP_90_DAYS.md`
2. Sprint board: `docs/SPRINT_BOARD_90_DAYS.md`
3. Jira CSV import: `docs/JIRA_IMPORT_90_DAYS.csv`
4. Sprint 1 kickoff plan: `docs/SPRINT_1_KICKOFF_PLAYBOOK.md`

## Live P0 Status Tracker (As Of 2026-03-19)

Status legend: `todo`, `in_progress`, `blocked`

| ID | Title | Owner | Priority | Status | Sprint | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| S3-001 | Dedicated Futures discover -> ticket -> order flow | Android + Backend | P0 | todo | Sprint 3 | Product-depth parity |
| S3-002 | Strategy payoff, Greeks, and risk warnings | Android | P0 | todo | Sprint 3 | Options safety and confidence |
| S3-003 | Multi-leg payload validation and guardrails | Backend | P0 | todo | Sprint 3 | Risk containment |

## Competitive UI Delta Tracker (Added 2026-03-16)

Competitor signals mapped:

1. Groww: faster discovery, simpler market-home hierarchy, lower-friction investing entry points.
2. Angel One: trader-grade execution UX, calculators, and chart-linked order confidence.
3. INDmoney: visual net-worth layer, family wealth storytelling, and cross-product money operating system.
4. Univest: advisory-first execution, expert-style ratings, premium screeners, and shark-portfolio discovery.

| ID | Title | Owner | Priority | Status | Sprint | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| UI-004 | Add trader mode and chart-linked execution UX | Android | P1 | in_progress | Sprint 6 | Trade Hub tabs, chart-linked ticket context, and live execution feedback are now wired in the spot flow |
| UI-005 | Turn Wealth OS into visual net-worth layer | Android + Backend | P1 | in_progress | Sprint 6 | Family, allocation, goals, and timeline storytelling |
| UI-006 | Surface inline Copilot, trust center, and calculators | Android + Backend | P1 | in_progress | Sprint 6 | Search trust/calculator discovery, Trade decision-tools entry points, and inline detail/ticket trust layers are now wired with Copilot actions and trace-aware support routing |
| UI-007 | Build screener and signal lab | Android + Backend | P1 | in_progress | Sprint 6 | Core Signal Lab and Golden Cross shipped; next target is Results Week and Institutional Conviction buckets |
| UI-008 | Launch investor portfolios and idea feed | Android + Data | P1 | in_progress | Sprint 6 | Investor portfolio discovery, portfolio-change deltas, and explainable idea feed are now wired into Smart Money with one-tap stock routing |

## Immediate 14-Day Execution Plan (2026-03-16 To 2026-03-29)

Objective: close the remaining release-hardening gaps before expanding Futures and Options depth.

### Priority Workstreams

| Track | Item | Owner | Target | Definition Of Done |
| --- | --- | --- | --- | --- |
| Auth Ops | Password reset production hardening | Backend + DevOps | Day 13 | SMTP configured, debug reset code disabled in prod, alerting for reset-delivery failures |
| Release Readiness | Soak and regression signoff | QA + Android + Backend | Day 14 | Market-hour soak, auth regression, and P0 checklist pass for release candidate cut |
| Release Bundle | Android App Bundle (AAB) signed and ready for console upload | Android | Day 14 | app-release.aab (2.6.83, v123) built and ready for Google Play upload |
| Competitive UX | Inline Copilot + Signal Lab phase-2 + Smart Money phase-2 | Product + Android + Backend | Day 14 | UI-006 inline guidance in search/detail/ticket, UI-007 Results Week + Institutional Conviction buckets, UI-008 portfolio-change feed + explainable idea cards |

### Next Pending Improvements To Target Now (2026-03-19 To 2026-04-02)

| Focus ID | Scope | Owner | Start | Target Completion | Exit Signal |
| --- | --- | --- | --- | --- | --- |
| UI-006 | Inline Copilot and trust surfaces in `Search`, `Stock Detail`, and trade ticket flows; decision-time calculator hooks | Android + Backend | 2026-03-19 | 2026-03-25 | Copilot guidance and calculator/trust actions appear in-flow (not standalone only) with route and telemetry coverage |
| UI-007 | Signal Lab phase-2: backend Results Week endpoint and Institutional Conviction proxy bucket, wired into tab 20 | Backend + Android | 2026-03-20 | 2026-03-27 | Results-week candidates and institutional-conviction list render in Signal Lab and open symbol detail directly |
| UI-008 | Smart Money phase-2: investor portfolio change tracking plus explainable idea feed cards | Data + Backend + Android | 2026-03-22 | 2026-04-02 | Users can see what changed in investor holdings and why an idea surfaced, with one-tap route into detail/execution |

### Daily Exit Criteria (Mandatory)

1. No unresolved P0 blocker at day close.
2. New behavior has tests (unit/integration/e2e as applicable).
3. Monitoring hooks and dashboard panel updates are shipped with backend changes.
4. Android and backend validation commands remain green on each milestone branch.

### Release Gate For This 14-Day Window

1. Crash-free sessions >= 99.8 percent.
2. Order success rate >= 99.5 percent during market hours.
3. p95 quote latency <= 300 ms for tracked symbols.
4. Failed-order traceability <= 2 minutes.
5. Zero unresolved P0 items in S1 and S2 tracks.

## Next 28-Day P0 Ownership Board (2026-03-16 To 2026-04-12)

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
| Sprint 3 | 2026-04-06 | 2026-04-19 | 2026-04-17 to 2026-04-19 | Android, Backend | Dedicated Futures and options risk depth |
| Sprint 4 | 2026-04-20 | 2026-05-03 | 2026-05-01 to 2026-05-03 | Android, Backend, Compliance | KYC hardening and sell authorization controls |
| Sprint 5 | 2026-05-04 | 2026-05-17 | 2026-05-15 to 2026-05-17 | Android, Backend, QA | SGB module and reporting center |
| Sprint 6 | 2026-05-18 | 2026-05-31 | 2026-05-29 to 2026-05-31 | Android, Data, DevOps, QA | Performance hardening and growth features |

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

### Epic S6-E3: Experience, Trust, and Advisory Layer

- ID: UI-004
  Type: Story
  Title: Add trader mode and chart-linked execution UX
  Owner: Android
  Points: 8
  Priority: P1
  Acceptance:
  - User can move from chart context to order entry with minimal field repetition.
  - Positions, orders, charges, and execution feedback stay visible during active trading.

- ID: UI-005
  Type: Story
  Title: Turn Wealth OS into visual net-worth layer
  Owner: Android + Backend
  Points: 8
  Priority: P1
  Acceptance:
  - Family wealth, allocation, goals, and progress trends are readable at a glance.
  - Wealth OS feels like a consumer-facing money view, not an internal form surface.

- ID: UI-006
  Type: Story
  Title: Surface inline Copilot, trust center, and calculators
  Owner: Android + Backend
  Points: 8
  Priority: P1
  Acceptance:
  - Copilot guidance appears inside search, detail, ticket, and rejection flows.
  - Brokerage, margin, trust information, and portfolio-doctor style guidance are accessible in-product at decision time.

- ID: UI-007
  Type: Story
  Title: Build screener and signal lab
  Owner: Android + Backend
  Points: 8
  Priority: P1
  Acceptance:
  - Users can browse breakout, dividend, results, holdings-flow, and verdict-driven screeners from one discovery surface.
  - Screeners refresh frequently and route directly into detail, watchlist, and trade workflows.

- ID: UI-008
  Type: Story
  Title: Launch investor portfolios and idea feed
  Owner: Android + Data
  Points: 8
  Priority: P1
  Acceptance:
  - Users can track top investor and fund portfolios along with recent position changes inside the app.
  - Expert and AI-supported idea feeds explain why a symbol is surfacing and route directly into decision and execution flows.

## KPI Tracking Template (Per Sprint Review)

- Crash-free sessions:
- Order success rate:
- p95 order latency:
- p95 quote latency:
- Onboarding completion:
- D1/D7/D30 retention:
- Search-to-trade conversion:
- Home action CTR:
- Support tickets per 1,000 users:
- MTTD/MTTR:

## Definition of Done (All P0 Stories)

- Unit tests and integration tests pass.
- Critical regression suite passes.
- Monitoring and alerting coverage added.
- Product and compliance sign-off recorded.
