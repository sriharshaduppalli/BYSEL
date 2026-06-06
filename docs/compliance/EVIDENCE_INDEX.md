# BYSEL Compliance Evidence Index

Date initialized: 2026-06-01
Purpose: central index of artifacts used for regulatory and audit readiness.

## How to use

1. Store or reference immutable artifacts for each control.
2. Update this index whenever a new evidence artifact is generated.
3. Use commit links or signed file hashes where possible.

## Evidence Register

| Evidence ID | Control ID | Artifact Description | Source Path or URL | Owner | Created On | Verified By | Verification Date | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EV-0001 | ENG-BASELINE | SEBI/broker readiness gap checklist baseline | docs/SEBI_BROKER_READINESS_GAP_CHECKLIST.md | Engineering | 2026-06-01 | Pending | Pending | Initial baseline |
| EV-0002 | CTRL-BASELINE | Control tracker initialization | docs/compliance/CONTROL_TRACKER.md | Engineering | 2026-06-01 | Pending | Pending | Initial control catalog |
| EV-0003 | REL-001 | Android release bundle artifact path documented | android/app/build/outputs/bundle/release/app-release.aab | Android | 2026-06-01 | Pending | Pending | Capture checksum before filing |
| EV-0004 | QA-001 | Backend test suite evidence (115 passed) | Local terminal output and CI logs | Backend | 2026-06-01 | Pending | Pending | Link CI run URL when available |

## Required Evidence Buckets (minimum)

1. Legal and licensing documents.
2. Capital/net-worth and financial compliance certificates.
3. KYC/AML policy and operational logs.
4. Security assessment reports (VAPT + retest closure).
5. DR/BCP drill reports with RTO/RPO measurements.
6. Grievance handling records and SLA compliance reports.
7. Audit trail retention and retrieval proof.
8. Exchange membership/integration approvals.

## Integrity Notes

- Prefer PDFs exported from source systems with timestamps.
- For generated files, record SHA-256 and storage location.
- Keep evidence ownership explicit to avoid audit ambiguity.
