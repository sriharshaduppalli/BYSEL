# BYSEL 90-Day Competitive Roadmap

Date: 2026-03-05
Scope: Product, engineering, reliability, compliance, and growth to compete with Zerodha, Groww, and Angel One.

## 1) Day-90 Success Criteria

1. Trading reliability: order success rate >= 99.5 percent in market hours.
2. Stability: crash-free sessions >= 99.8 percent on Android.
3. Latency: p95 quote update latency <= 300 ms for active symbols.
4. Trust: complete post-trade transparency for charges, margin, and execution status.
5. Coverage: production-ready Futures flow, Options strategy flow, and SGB module.
6. Growth: D30 retention improvement by at least 20 percent from baseline.

## 2) Current High-Impact Gaps

1. Dedicated Futures trading journey is not fully productized.
2. SGB or Gold Bonds flow is missing.
3. Brokerage-grade OMS or RMS resilience is incomplete for volatile market windows.
4. Compliance and onboarding depth needs hardening for scale.
5. Observability, SLO monitoring, and incident runbooks are not yet complete.

## 3) Operating Model

1. Sprint cadence: 6 sprints, each 2 weeks.
2. Release cadence: weekly internal builds, bi-weekly production releases.
3. Owners:
	- Android Lead: app UX, trading flows, performance.
	- Backend Lead: OMS, APIs, idempotency, audit logging.
	- Data and Platform Lead: real-time data quality, monitoring, SLO dashboards.
	- QA Lead: regression matrix, release gate checks, automation.
	- Compliance and Ops Lead: KYC, eDIS or TPIN, policies, support SOPs.

## 4) Sprint Plan

### Sprint 1 (Weeks 1-2): Core Reliability Baseline

1. Implement order lifecycle state machine with idempotency keys.
2. Add reconnect-safe socket with sequence tracking and gap recovery.
3. Add observability dashboards for order flow, API errors, and quote latency.
4. Establish release gates for crash-free threshold and critical bug count.

Exit criteria:
1. No duplicate orders under retry conditions.
2. Real-time stream recovers automatically after disconnect.
3. Dashboard alerts available for p95 latency and error spikes.

### Sprint 2 (Weeks 3-4): Execution Parity and Risk Transparency

1. Harden advanced order validations and rejection reasons.
2. Improve margin preview and fee preview before order confirmation.
3. Add full error taxonomy in UI with actionable recovery messaging.
4. Add audit-trace IDs visible in logs and support tooling.

Exit criteria:
1. All order rejections map to deterministic user-facing reasons.
2. Margin and charges shown pre-trade and post-trade with consistent values.
3. Support can trace any failed order in under 2 minutes.

### Sprint 3 (Weeks 5-6): Futures and Options Depth

1. Launch dedicated Futures trading flow.
2. Ship Option Chain and strategy builder with real risk metrics.
3. Add Greeks, payoff preview, and strategy warning checks.
4. Add multi-leg order guardrails and validation.

Exit criteria:
1. Futures order flow is complete end-to-end in production.
2. Options strategy execution supports validated multi-leg payloads.
3. Risk warnings trigger before unsafe submission.

### Sprint 4 (Weeks 7-8): Compliance and Trust Layer

1. End-to-end onboarding hardening with KYC and bank verification checks.
2. Implement eDIS or TPIN related sell authorization flow.
3. Add secure session controls: forced logout on token risk, device session list.
4. Add security hardening: TLS pinning, root detection, fraud event logging.

Exit criteria:
1. Onboarding success funnel improves with lower drop-off at verification steps.
2. Sell authorization failures are transparent and recoverable.
3. Security checks are active and monitored in production.

### Sprint 5 (Weeks 9-10): Wealth and Reporting Expansion

1. Launch SGB module including discovery, application, and tracking.
2. Add tax P and L summary and downloadable statements.
3. Add contract-note and ledger consistency checks in app.
4. Improve portfolio insights with personalized next-best actions.

Exit criteria:
1. SGB flow available with clear status tracking.
2. User can access statement and tax summary from app.
3. Ledger, tradebook, and P and L views reconcile with backend records.

### Sprint 6 (Weeks 11-12): Scale, Polish, and Growth

1. Performance optimization for low-end devices and slow networks.
2. Add intelligent alerts, watchlist scanners, and retention nudges.
3. Finalize support SOPs and incident management runbooks.
4. Execute launch readiness checklist and post-release monitoring.

Exit criteria:
1. App startup and screen render targets meet defined budgets.
2. Alert features drive measurable engagement lift.
3. Incident response runbooks validated via drill.

## 5) Owner-Wise Backlog (P0 First)

### Android Team

1. P0: Dedicated Futures flow with complete ticket, validation, and order tracking.
2. P0: Real-time order and quote UI resiliency under reconnect conditions.
3. P0: Pre-trade charge and margin breakdown in ticket.
4. P1: SGB application and status screens.
5. P1: Tax reports and statement center UX.

### Backend Team

1. P0: Idempotent order APIs and deterministic order state transitions.
2. P0: Market data stream consistency and replay for missed events.
3. P0: Audit logs with trace IDs across auth, order, and funds APIs.
4. P1: SGB APIs and allocation status handling.
5. P1: Reporting endpoints for ledger, charges, and P and L.

### QA and Automation

1. P0: Critical-path regression suite for auth, order placement, cancellation, and funds.
2. P0: Load and chaos tests for opening-hour volatility scenarios.
3. P0: Release gate automation with pass or fail thresholds.
4. P1: Device-matrix performance benchmark pipeline.

### DevOps and Platform

1. P0: SLO dashboards and alerting for core user journeys.
2. P0: Rollback-safe release strategy and deployment verification.
3. P0: Secrets and key management policy checks in CI.
4. P1: Cost and performance tuning for peak market traffic.

## 6) KPI Dashboard (Weekly Review)

1. Crash-free sessions.
2. Order success rate.
3. p95 order placement latency.
4. p95 quote stream latency.
5. Onboarding completion rate.
6. D1, D7, D30 retention.
7. Support tickets per 1,000 active users.
8. Mean time to detect and mean time to resolve incidents.

## 7) Immediate Next 7 Days

1. Freeze KPI baselines from current production telemetry.
2. Finalize P0 engineering scope for Sprints 1 and 2.
3. Start implementation of order idempotency and stream reconnection.
4. Lock acceptance criteria for Futures and SGB modules.
5. Set weekly product, engineering, and compliance review rhythm.

## 8) Go or No-Go Rules for Market Push

1. No unresolved P0 defects in trading, auth, or funds.
2. Crash-free and order-success thresholds achieved for 2 consecutive weeks.
3. Compliance sign-off complete for onboarding and sell authorization flows.
4. Incident drill passed with documented on-call handoff.
