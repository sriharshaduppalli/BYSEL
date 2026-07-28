# BYSEL SEBI Readiness Execution Plan (12 Weeks)

Date: 2026-06-01
Plan owner: Founders + Compliance Lead
Linked baseline:
- docs/SEBI_BROKER_READINESS_GAP_CHECKLIST.md
- docs/compliance/CONTROL_TRACKER.md
- docs/compliance/EVIDENCE_INDEX.md

Important: This is an execution plan for readiness and filing preparation. Final eligibility and approval outcomes are determined by SEBI, exchanges, and appointed auditors.

## 1) Goal and Success Definition

Goal:
- Move BYSEL from Partial/Missing readiness to filing-ready status with evidence-backed controls.

Success at week 12 means:
1. All P0 controls in CONTROL_TRACKER are Done or formally accepted with legal/compliance sign-off.
2. Evidence index contains verifiable artifacts for each P0 control.
3. Internal mock-audit completes with no critical findings.
4. Filing package is assembled and reviewed by legal counsel.

## 2) Workstreams and Owners

1. Legal and Licensing
- Owner: Founders + External Regulatory Counsel
- Controls: LEG-001, LEG-002

2. Exchange and Operations
- Owner: Compliance + Operations
- Controls: EXG-001, REC-001

3. KYC/AML and Investor Protection
- Owner: Compliance + Backend + Android + Support
- Controls: AML-001, KYC-001, GOV-001, DSC-001

4. Security and Cyber Audit
- Owner: Security Lead + Engineering + DevOps
- Controls: SEC-001, SEC-002, APP-001, AUD-001

5. Reliability/BCP/DR
- Owner: DevOps + Backend + Compliance
- Controls: DR-001, OPS-001

## 3) Week-by-Week Plan

## Week 1 (Days 1-7): Program Setup and Regulatory Path Lock

Targets:
1. Finalize licensing path and legal strategy memo (LEG-001).
2. Appoint named compliance owner and weekly governance cadence.
3. Freeze control tracker baseline and assign owners/dates.
4. Build evidence folder structure and naming convention.

Deliverables:
1. Signed legal strategy memo.
2. Weekly review calendar and attendee list.
3. Updated CONTROL_TRACKER with owner commitments.
4. Evidence index process documented.

Exit criteria:
1. LEG-001 moves to Partial or Done with artifact link.
2. Every P0 control has owner and date.

## Week 2 (Days 8-14): Financial and Exchange Prerequisites

Targets:
1. Start capital/net-worth documentation and CA certification workflow (LEG-002).
2. Build exchange onboarding checklist pack (EXG-001).
3. Draft clearing and settlement SOP outline.

Deliverables:
1. CA document request tracker.
2. Exchange checklist draft with owner per item.
3. Settlement SOP v0.1.

Exit criteria:
1. LEG-002 and EXG-001 both have active artifact links.

## Week 3 (Days 15-21): AML/KYC Operating Model Hardening

Targets:
1. Draft and approve AML/PMLA monitoring SOP (AML-001).
2. Convert KYC flow hardening from roadmap item to production backlog (KYC-001).
3. Define suspicious activity escalation chain.

Deliverables:
1. AML rules matrix and threshold table.
2. KYC retry/failure state definitions and owner map.
3. Escalation contacts and SLA sheet.

Exit criteria:
1. AML-001 moves to Partial with reviewed SOP.
2. KYC-001 implementation tasks created and scheduled.

## Week 4 (Days 22-28): Investor Protection and Disclosure Controls

Targets:
1. Implement grievance workflow and SLA dashboard spec (GOV-001).
2. Finalize versioned risk disclosure and consent capture design (DSC-001).
3. Define complaint intake channels and escalation levels.

Deliverables:
1. Grievance SOP and SLA metrics definition.
2. Disclosure text set approved by legal/compliance.
3. Consent-event schema for immutable logging.

Exit criteria:
1. GOV-001 and DSC-001 move to Partial with evidence artifacts.

## Week 5 (Days 29-35): Security Assessment Kickoff

Targets:
1. Finalize external VAPT scope and vendor engagement (SEC-001).
2. Complete security baseline checks: access matrix, secret handling, key rotation.
3. Prepare app/API hardening test cases.

Deliverables:
1. Signed VAPT statement of work and timeline.
2. Access control matrix and secret rotation log template.
3. Security test plan.

Exit criteria:
1. SEC-001 evidence link added (engagement proof).

## Week 6 (Days 36-42): Logging, Traceability, and Incident Preparedness

Targets:
1. Implement immutable order-lifecycle audit events (AUD-001).
2. Complete incident runbooks and on-call matrix (OPS-001).
3. Define retention and retrieval controls for audit logs.

Deliverables:
1. Audit event catalog and retention policy draft.
2. Incident runbook v1.
3. On-call and escalation matrix.

Exit criteria:
1. AUD-001 and OPS-001 move to Partial/Done with evidence.

## Week 7 (Days 43-49): VAPT Execution and Defect Intake

Targets:
1. Execute external VAPT and collect findings (SEC-001).
2. Prioritize remediation backlog with severity and due dates.
3. Verify app hardening controls in release profile (APP-001).

Deliverables:
1. VAPT preliminary report.
2. Remediation tracker with owner and ETA.
3. App hardening verification notes.

Exit criteria:
1. SEC-001 moves to Done once signed report received.
2. SEC-002 plan approved.

## Week 8 (Days 50-56): Security Remediation Sprint

Targets:
1. Close critical/high VAPT findings (SEC-002).
2. Perform retest and document closure status.
3. Validate API abuse/rate-limit and suspicious-login controls.

Deliverables:
1. Remediation evidence bundle.
2. Retest report or closure statement.
3. API security control evidence.

Exit criteria:
1. SEC-002 moves to Partial/Done with retest evidence.

## Week 9 (Days 57-63): DR/BCP and Reconciliation Controls

Targets:
1. Finalize DR architecture and RTO/RPO objectives (DR-001).
2. Run first DR drill and capture outcomes.
3. Operationalize reconciliation reports (REC-001).

Deliverables:
1. DR plan and topology notes.
2. DR drill report with observed RTO/RPO.
3. Daily reconciliation exception workflow.

Exit criteria:
1. DR-001 and REC-001 both have evidence artifacts.

## Week 10 (Days 64-70): Exchange Dossier and Filing Pack Assembly

Targets:
1. Assemble exchange membership package draft (EXG-001).
2. Consolidate all legal/financial artifacts (LEG-001, LEG-002).
3. Prepare grievance and investor-protection evidence packet.

Deliverables:
1. Exchange application checklist v1 complete.
2. Compliance filing binder draft.
3. Evidence index updated for all P0 controls.

Exit criteria:
1. No P0 control remains Missing without approved mitigation.

## Week 11 (Days 71-77): Internal Mock Audit and Gap Closure

Targets:
1. Run mock audit against control tracker and evidence index.
2. Resolve all critical findings.
3. Obtain cross-functional sign-offs.

Deliverables:
1. Mock audit report.
2. Corrective action log.
3. Sign-off memo draft.

Exit criteria:
1. Critical findings count = 0.
2. Filing blocker list reduced to zero or formally accepted risk.

## Week 12 (Days 78-84): Filing Readiness and Submission Window

Targets:
1. Final legal/compliance review.
2. Freeze evidence package and version it.
3. Prepare submission responses and query handling playbook.

Deliverables:
1. Final filing pack.
2. Query-response matrix with owners.
3. Submission readiness sign-off.

Exit criteria:
1. Program declares filing-ready status.

## 4) Immediate Actions For This Week (Start Now)

1. Fill owner names and dates for every P0 row in CONTROL_TRACKER.
2. Add evidence links for existing artifacts in EVIDENCE_INDEX.
3. Schedule weekly compliance review meeting for next 8 weeks.
4. Issue legal memo request for license path decision.
5. Open CA work item for net-worth certificate timeline.
6. Open VAPT vendor shortlisting task with due date.
7. Start grievance SOP draft and SLA definitions.
8. Create KYC hardening implementation stories (backend + Android).
9. Define immutable event schema for order/audit logs.
10. Create DR plan skeleton with target RTO/RPO.

## 5) Weekly KPI Dashboard (Track Every Friday)

1. P0 controls Done count.
2. P0 controls Missing count.
3. Evidence coverage ratio: controls with valid artifact link / total controls.
4. Critical security findings open.
5. DR drill success against target RTO/RPO.
6. Grievance SLA compliance percentage.
7. AML alert-to-resolution turnaround.

## 6) Governance Cadence

1. Weekly control review (60 min).
2. Fortnightly remediation review (90 min).
3. Monthly legal/compliance steering review (120 min).

Meeting outputs each cycle:
1. Updated CONTROL_TRACKER.
2. Updated EVIDENCE_INDEX.
3. Decision and risk log.

## 7) Risks and Mitigations

1. Risk: Legal path delay.
- Mitigation: Time-box to week 1 with escalation to counsel partner.

2. Risk: VAPT scheduling slippage.
- Mitigation: Shortlist two vendors in parallel and pre-book slot.

3. Risk: Evidence quality fails audit.
- Mitigation: Add verifier role and acceptance checklist per artifact.

4. Risk: Engineering bandwidth conflicts with release work.
- Mitigation: Reserve fixed weekly compliance sprint capacity.

## 8) Definition of Done Per Control

A control can be marked Done only if all are true:
1. Process/control implemented.
2. Owner assigned and active.
3. Evidence artifact linked in EVIDENCE_INDEX.
4. Artifact verified by reviewer with date.
5. Residual risk accepted or resolved.
