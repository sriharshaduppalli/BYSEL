# BYSEL SEBI and Broker Certification Readiness Gap Checklist

Date: 2026-06-01
Prepared from: current repository state and project documentation
Scope: product and engineering readiness mapping for broker registration preparation

Important: This is an operational readiness checklist, not legal advice. Final eligibility and certification decisions are made by SEBI, exchanges, and appointed auditors/compliance officers.

## 1) Executive Summary

Current position:
- Engineering maturity is improving: backend test coverage is active, Android AAB pipeline exists, release artifacts are generated.
- Regulatory readiness is not yet complete: several mandatory governance, legal, cyber-audit, market-access, and operations controls need formal implementation and evidence.

High-level readiness verdict:
- Product engineering release readiness: Partial
- SEBI and exchange certification readiness: Not ready yet

## 2) Repo-Based Evidence Snapshot

Evidence available in repository:
- Product architecture and deployment docs: ARCHITECTURE, STATUS, deployment guides.
- Sprint board includes compliance/security workstreams and controls.
- Backend and Android build/test flow documented.

Known open engineering items at working tree level:
- Uncommitted Android/build/config/database/doc changes are present and require hygiene before formal release branch cut.

## 3) Regulatory Readiness Gap Matrix

Legend:
- Done: Implemented with evidence and owner
- Partial: Some implementation exists but evidence/control is incomplete
- Missing: Not evidenced in current repository artifacts

### A) Legal and Entity Setup

1. Registered entity and permitted business object for broking
- Status: Missing
- Required evidence:
  - Incorporation documents and constitutional documents
  - Board approvals for broking activities
  - Business object alignment with broking/distribution scope
- Owner: Founders + Legal counsel

2. Capital adequacy and net worth compliance (as applicable for selected license path)
- Status: Missing
- Required evidence:
  - CA-certified net worth statements
  - Capital adequacy and liquidity plan
- Owner: Finance + Compliance

3. License path clarity (broker, sub-broker/AP, platform partner model)
- Status: Partial
- Required evidence:
  - Selected regulatory path with legal memo
  - Gap closure plan per path
- Owner: Founders + Regulatory advisor

### B) Exchange and Market Access

1. Exchange membership/onboarding package readiness
- Status: Missing
- Required evidence:
  - Exchange application checklist completion
  - Membership processing documentation
- Owner: Compliance + Operations

2. Clearing and settlement arrangement readiness
- Status: Missing
- Required evidence:
  - Clearing member agreements
  - Settlement SOP and reconciliation controls
- Owner: Operations + Finance

3. Trade lifecycle controls (order capture, audit trail, modification/cancellation traceability)
- Status: Partial
- Required evidence:
  - Immutable order/trade logs
  - Time-synced event trail
  - Exception handling SOP
- Owner: Backend + DevOps + Compliance

### C) KYC, AML, and Investor Protection

1. KYC and identity verification flow hardening
- Status: Partial
- Existing signal:
  - Sprint board includes KYC hardening and verification initiatives
- Required evidence:
  - KYC vendor integration controls
  - Rejection/retry controls and evidence logs
- Owner: Backend + Android + Compliance

2. AML/PMLA monitoring and suspicious activity workflow
- Status: Missing
- Required evidence:
  - Transaction monitoring rules
  - Escalation and reporting SOP
  - Compliance case management trail
- Owner: Compliance + Backend

3. Investor grievance redressal and escalation SLA
- Status: Missing
- Required evidence:
  - Grievance policy
  - Ticket workflow with SLA dashboard
  - Escalation contacts and records
- Owner: Support + Compliance

4. Risk disclosures and suitability warnings
- Status: Partial
- Existing signal:
  - Product includes derivatives strategy and risk-warning focus
- Required evidence:
  - Standardized risk disclosure text
  - Versioned consent capture
- Owner: Product + Legal + Compliance

### D) Cybersecurity and Technology Controls

1. Security baseline (secure SDLC, secrets management, least privilege)
- Status: Partial
- Existing signal:
  - CI/CD and secrets setup guidance exists
- Required evidence:
  - Access matrix and role segregation
  - Key rotation policy and logs
- Owner: DevOps + Security

2. VAPT and remediation closure
- Status: Missing
- Required evidence:
  - Third-party VAPT report
  - Remediation closure report with retest sign-off
- Owner: Security + Engineering

3. App/API hardening controls
- Status: Partial
- Existing signal:
  - Sprint security items mention session controls, suspicious login, certificate pinning, root detection
- Required evidence:
  - Enforcement proof in release builds
  - API abuse/rate-limit logs
- Owner: Android + Backend + Security

4. Audit logging and retention
- Status: Partial
- Required evidence:
  - Tamper-evident audit logs
  - Retention and retrieval policy
  - Audit access controls
- Owner: Backend + DevOps + Compliance

### E) Reliability, BCP/DR, and Operations

1. Production monitoring and incident response
- Status: Partial
- Existing signal:
  - Sprint board includes MTTR drills and release gates
- Required evidence:
  - 24x7 alerting matrix
  - Incident runbooks and postmortems
- Owner: DevOps + Backend

2. Business continuity and disaster recovery drills
- Status: Missing
- Required evidence:
  - DR architecture
  - RTO/RPO targets
  - Periodic DR drill reports
- Owner: DevOps + Compliance

3. Reconciliation and reporting controls
- Status: Partial
- Existing signal:
  - Sprint board includes ledger/tradebook reconciliation tasks
- Required evidence:
  - Daily reconciliation reports
  - Exception queue and closure workflow
- Owner: Operations + Finance + Backend

### F) Governance, Documentation, and Audit Pack

1. Compliance governance framework
- Status: Missing
- Required evidence:
  - Compliance manual
  - Designated compliance officer responsibilities
  - Periodic review calendar
- Owner: Compliance

2. Policy stack
- Status: Missing
- Required evidence:
  - Information security policy
  - Data privacy and retention policy
  - Vendor risk and change management policy
- Owner: Legal + Compliance + Security

3. Regulatory audit pack readiness
- Status: Missing
- Required evidence:
  - Control matrix mapped to each requirement
  - Evidence index with owners and dates
  - Internal pre-audit sign-off
- Owner: Compliance PMO

## 4) Immediate Priority Actions (Next 30 Days)

1. Freeze release hygiene and branch strategy
- Close all uncommitted workspace items
- Align active branch strategy for release and compliance evidence tracking

2. Appoint compliance lead and finalize licensing path
- Decide exact market-entry path
- Create requirement-by-requirement control tracker

3. Commission external cybersecurity assessment
- Run VAPT and threat assessment
- Create remediation sprint with closure evidence

4. Implement mandatory investor protection workflows
- Grievance module and SLA tracking
- Disclosure and consent capture with immutable logs

5. Formalize KYC/AML operating model
- Monitoring rules, escalation SOP, and reporting cadence

## 5) 60-Day Evidence Pack To Produce

1. Legal and registration documents set
2. Capital/net worth certification set
3. Exchange and clearing integration evidence
4. Security audit reports and closure reports
5. DR/BCP test reports
6. Audit trail and retention proof
7. KYC/AML policy and execution logs
8. Grievance handling and SLA compliance reports
9. Control matrix and internal sign-off

## 6) Engineering Workstream Additions Recommended

1. Create a compliance-controls repository section
- Add docs/compliance/ with policy docs, control matrix, SOPs, and evidence index

2. Add traceability for high-risk actions
- Order placement, modification, cancellation, authorization, KYC decisions, consent events

3. Add production-grade reliability gates
- SLO dashboards, alert thresholds, incident playbooks, rollback procedures

4. Add quarterly compliance test cadence
- Security regression, KYC negative tests, fraud/abuse simulation, DR restore drills

## 7) Suggested Operating Model

Cadence:
- Weekly: control status review with owners
- Fortnightly: remediation sprint review
- Monthly: internal mock-audit against evidence index

Reporting template per control:
- Control name
- Requirement source
- Owner
- Current status (Done/Partial/Missing)
- Evidence link
- Risk rating
- Target closure date

## 8) Practical Go/No-Go Criteria Before Filing

Proceed to filing only when all are true:
1. No Missing item in Legal, AML/KYC, Security audit, DR/BCP, or Grievance categories.
2. All Partial items have dated closure plans and accountable owners.
3. External audit findings are closed or accepted with formal risk sign-off.
4. Evidence pack is complete, versioned, and reviewed by legal/compliance counsel.

---

If useful, the next document to create is a control-by-control tracker in spreadsheet form with owner, due date, and evidence URL for each checklist item above.
