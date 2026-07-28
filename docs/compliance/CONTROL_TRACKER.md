# BYSEL Compliance Control Tracker

Date initialized: 2026-06-01
Source baseline: docs/SEBI_BROKER_READINESS_GAP_CHECKLIST.md

Status legend:
- Done
- Partial
- Missing
- Blocked

Priority legend:
- P0: Mandatory before filing
- P1: Required for operational maturity
- P2: Optimization

## P0 Controls

| ID | Control | Category | Current Status | Owner | Target Date | Evidence Link | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| LEG-001 | Confirm legal licensing path (broker/sub-broker/AP/platform) | Legal | Missing | Founders + Legal | 2026-06-15 | TBD | Formal legal memo required |
| LEG-002 | Capital adequacy and net-worth certification | Legal/Finance | Missing | Finance + CA | 2026-06-20 | TBD | Certification package pending |
| EXG-001 | Exchange membership/onboarding checklist | Exchange | Missing | Compliance + Ops | 2026-06-25 | TBD | Exchange-specific checklist |
| AML-001 | AML/PMLA transaction monitoring SOP | AML/KYC | Missing | Compliance | 2026-06-18 | TBD | Rule matrix and escalation workflow |
| KYC-001 | KYC hardening with retry and exception handling | AML/KYC | Partial | Backend + Android | 2026-06-22 | docs/SPRINT_BOARD_90_DAYS.md | Needs production evidence |
| SEC-001 | Third-party VAPT completed and signed report | Security | Missing | Security Lead | 2026-06-28 | TBD | External assessment pending |
| SEC-002 | VAPT remediation closure and retest sign-off | Security | Missing | Engineering + Security | 2026-07-05 | TBD | Dependent on SEC-001 |
| DR-001 | DR architecture, RTO/RPO and drill report | BCP/DR | Missing | DevOps | 2026-07-05 | TBD | Drill evidence required |
| GOV-001 | Grievance redressal process and SLA dashboard | Investor Protection | Missing | Support + Compliance | 2026-06-24 | TBD | Include escalation chain |
| AUD-001 | Immutable audit trail for order lifecycle events | Audit/Tech | Partial | Backend + DevOps | 2026-06-26 | TBD | Needs retention and access policy |

## P1 Controls

| ID | Control | Category | Current Status | Owner | Target Date | Evidence Link | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| APP-001 | Certificate pinning and root detection telemetry in release builds | App Security | Partial | Android | 2026-06-30 | docs/SPRINT_BOARD_90_DAYS.md | Verify prod enforcement |
| OPS-001 | Incident response runbooks and on-call matrix | Operations | Partial | DevOps + Backend | 2026-06-21 | TBD | Add P1 incident playbook |
| REC-001 | Daily ledger/tradebook reconciliation with exception queue | Operations/Finance | Partial | Ops + Finance + Backend | 2026-06-27 | docs/SPRINT_BOARD_90_DAYS.md | Needs automated report |
| DSC-001 | Versioned disclosure and consent capture for high-risk products | Investor Protection | Partial | Product + Legal + Backend | 2026-06-29 | TBD | Auditability required |

## Weekly Review Checklist

1. Update every control status.
2. Verify target dates and blockers.
3. Attach at least one artifact link for each Partial/Done item.
4. Escalate any P0 control at risk of slipping.
5. Record review date and attendees in meeting notes.
