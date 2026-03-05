# BYSEL Sprint Board (90 Days)

Date: 2026-03-05
Branch: android-build-fixes
Source: docs/COMPETITIVE_ROADMAP_90_DAYS.md

## How To Use

1. Import these items as Jira tickets (Epic -> Story -> Task).
2. Track status as `todo`, `in_progress`, `blocked`, `done`.
3. Keep weekly KPI snapshot in each sprint review.

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
