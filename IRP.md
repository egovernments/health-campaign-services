# Incident Response Plan (IRP)

**Repository:** `egovernments/health-campaign-services` (DIGIT Health Campaign Management — HCM)
**Applies to:** all services, libraries, and CI/CD pipelines in this repository, and the deployed environments built from it.

## Overview

This Incident Response Plan (IRP) provides a structured and repeatable process for identifying, analyzing, containing, and responding to security incidents. The plan is designed to minimize the impact of incidents on citizen services, safeguard sensitive data (PII/PHI), and ensure compliance with regulatory requirements.

Health Campaign Services underpins door-to-door public health campaigns — beneficiary registration, household enumeration, commodity (stock) tracking, referral management, attendance and payments. The data it handles is predominantly **citizen PII and PHI**, collected by field workers in low-connectivity settings on BYOD and government-issued devices. Incidents here are therefore treated as citizen-impacting by default until proven otherwise.

The document is organized into four main phases that mirror the natural lifecycle of an incident:

1. **Lead Validation** – Initial detection, validation of alerts, and severity assessment.
2. **Mitigation** – Containment actions, both short- and long-term, to stop ongoing threats.
3. **Scoping** – Determining the breadth, depth, and impact of the incident across users, systems, and data.
4. **Notification** – Coordinating internal and external communication, including regulatory bodies, government partners, and affected citizens.

In addition to these phases, the IRP includes reference sections with practical guidance:

- **Support Escalation Matrix** describing how issues flow from field teams to eGov L3.
- **Severity Assignment Guidelines** to ensure consistent evaluation of risks.
- **Mitigation Methods Catalog** outlining common technical and organizational measures.
- **Repository Scope & Sensitive Data Map** identifying which services hold what.

---

## Repository Scope & Sensitive Data Map

Use this map during Phase 1 and Phase 3 to decide, quickly, whether PII/PHI is in play.

### Services holding or processing PII / PHI (highest sensitivity)

| Service | Path | Data held |
|---|---|---|
| Individual | `health-services/individual` | Citizen identity: name, DOB, gender, mobile, address, identifiers. **Encrypted via `egov-enc-service`.** |
| Household | `health-services/household` | Household composition, members, geo-location of dwellings |
| Worker Registry | `health-services/worker-registry` | Field-worker identity and contact details. **Encrypted via `egov-enc-service`.** |
| Referral Management | `health-services/referralmanagement` | Clinical referral records (PHI). **Encrypted via `egov-enc-service`.** |
| Health Notification Service | `health-services/health-notification-service` | Recipient contact details for notifications. **Encrypted via `egov-enc-service`.** |
| Project | `health-services/project` | Beneficiary↔task linkage, delivery/dose records, staff assignment |
| eGov HRMS | `core-services/egov-hrms` | Employee records, roles, documents |
| PGR Services | `core-services/pgr-services` | Citizen grievances, complainant contact details |
| Census Service | `health-services/census-service` | Population figures per boundary |
| Beneficiary IDGen | `core-services/beneficiary-idgen` | Beneficiary identifier issuance |

> Any confirmed exposure of data from the services above is a **PII/PHI incident** and is assigned the highest severity (see Severity Assignment Guide).

### Supporting services (lower direct sensitivity, high blast radius)

Boundary Management, Facility, Stock, Product, Plan Service, Project Factory, Resource Generator, Excel Ingestion, Transformer, Dashboard Analytics, Inbox, Service Request, Survey Services, NLP Engine, eGov Notification Push, Airflow Trigger Service.

Note two aggregation chokepoints: **Transformer** fans campaign events out to the analytics index, and **Dashboard Analytics** reads from it — a compromise at either point can expose data from many services at once. **Excel Ingestion / Resource Generator** move bulk files through the filestore; a leaked signed URL there can expose a whole campaign's dataset in one object.

### Shared infrastructure in scope

PostgreSQL (per-service schemas), Kafka (campaign event topics such as `save-project-task-topic`, `save-plan`, `update-stock-topic`), Elasticsearch/analytics index, Redis, filestore/MinIO/S3, the API gateway (Zuul), `egov-user` + `egov-enc-service` (IAM and field-level encryption), and the AWS/Kubernetes environments running them.

### Security tooling already wired into this repo

| Control | Where | What it gives you during an incident |
|---|---|---|
| Trivy | `.github/workflows/trivy.yml` | Dependency CVEs, leaked secrets, and per-image container CVEs → GitHub Security tab. Report-only. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Supply-chain posture signal |
| Codacy | `.github/workflows/codacy.yml` | Static analysis findings |
| Dependabot | GitHub-native | Vulnerable dependency alerts and upgrade PRs |
| Branch name validator | `.github/workflows/branch-name-validator.yml` | Enforces `<TICKET>-<description>` branch convention — hotfix branches must comply |
| CODEOWNERS | `CODEOWNERS` | Identifies required reviewers for an emergency patch |

**Reporting a vulnerability:** security issues in this repository must **not** be filed as public GitHub issues. Report privately to the eGov security contact (see `CODE_OF_CONDUCT.md` for eGov contact channels) or via the partner helpdesk path in the escalation matrix below.

---

## Support Escalation Matrix

Issue flow from field teams to L3 support resolution. A security incident may enter at any tier; once identified as security-relevant it is escalated **directly to eGov L3** in parallel with the normal tier progression, rather than walking up tier by tier.

```
Field Teams
    │  Registrars and distributors on the ground. Report on-ground issues
    │  (app crashes, sync failures, data-entry issues, device/network problems)
    │  to their supervisors.
    ▼
Supervisors — L0 Support
    │  Part of the L0 support team. Resolve immediate on-ground issues directly,
    │  or escalate to Partner Helpdesk L1 with issue context.
    ▼
Partner Helpdesk — L1 Support
    │  First line of Partner Helpdesk. Handle localization, campaign attribute
    │  updates, user create/update (HRMS + bulk), report generation, publishing
    │  stats and common queries via SOPs/FAQs. Escalate deeper config or
    │  platform issues to L2.
    ▼
Partner Helpdesk — L2 Support
    │  Advanced troubleshooting on the DIGIT HCM platform: Admin Console
    │  configurations, KPI dashboards, custom report scripts, attendance/payments
    │  configs, campaign infra monitoring, backend configs and data clean-up.
    │  Escalate to eGov L3 when further troubleshooting, code fixes, or infra
    │  optimization are required.
    ▼
eGov — L3 Support
       Final resolution tier: RCA on code & error logs, critical bug fixes,
       build/APK releases, performance/load testing, infra optimization &
       scale-down, and cache/monitoring readiness (Kafka, Grafana, Crashlytics,
       Redis).
```

### Security fast-path

| Trigger | Action |
|---|---|
| Suspected PII/PHI exposure reported at any tier | L0/L1 escalates **immediately** to eGov L3 *and* notifies the Implementation Security Lead and DPO. Do not attempt local remediation first. |
| Active exploitation / data exfiltration suspected | Page eGov L3 on-call; declare incident; begin Phase 2 containment before scoping completes. |
| Credential or token leak (including in a repo or build log) | eGov L3 + Implementation DevOps rotate first, investigate second. |
| Vulnerability found with no evidence of exploitation | Normal L2 → L3 escalation; track as a security bug with a severity rating. |

---

## Phase 1: Lead Validation

### Guidelines

- Initial detection can come from monitoring tools (e.g., AWS GuardDuty, SIEM, Grafana/Prometheus alerting, Crashlytics, anomaly monitoring), from GitHub security alerts (Trivy, Dependabot, Codacy), or reported by a partner or internal teams via the escalation matrix above.
- Incidents are validated by reviewing automated alerts, logs, and comparing against baseline activity. Look for supporting evidence (e.g., a suspicious login attempt **and** an abnormal bulk download from Excel Ingestion or the filestore).
- Try to reproduce the reported behavior if possible. Check if it needs special preconditions (e.g., unusual campaign configs, a specific tenant, insider/HRMS role access).
- Rule out false positives. Common ones in this platform: bulk sync bursts from field devices after a connectivity outage, scheduled Project Factory campaign generation runs, Transformer backfills, and load-test traffic.
- Determine which systems are affected (IAM/`egov-user`, API gateway, individual service PostgreSQL schemas, Kafka topics, analytics index, filestore, field devices) and identify attack surface.
- Confirm if sensitive data (PII/PHI) is impacted — cross-reference the **Repository Scope & Sensitive Data Map** above. Assign the highest severity level if impacted. For the rest, determine which part of the CIA triad is impacted and assign the severity rating accordingly (see Severity Assignment Guide).
- Have the incident reviewed by a second analyst or security lead to avoid biases.

### Roles & Responsibilities

**Implementation SRE / Implementation Security Analyst (Level 1)**
- Monitor incoming alerts and reports.
- Validate against baselines, rule out false positives.
- Document logs, suspicious activities, and initial findings.

**Implementation Lead / Security Lead**
- Approve or reject escalation to "Incident".
- Review severity assignment (especially if PII/PHI impacted).
- Ensure a second analyst reviews to avoid bias.

**Implementation DevOps Engineer**
- Provide system logs (IAM, API gateway, Kafka, Kubernetes, AWS infra).
- Confirm uptime/availability impact on campaign-critical services.

### Tasks

- Validate alerts: rule out false positives.
- Check impact on IAM, APIs, audit logs, data, uptime/availability.
- Identify affected services by name and path in this repository.
- Determine severity (Low/Medium/High/Critical as per severity matrix).
- Decide if the lead is not actionable or escalates to investigation.

### For the Record

- **Risk to CIA:** Yes – mostly Confidentiality & Availability.
- **Data at risk:** Citizen PII/PHI, household enumeration data, delivery/dose records, field-worker identity, grievance records.
- **Exploit requirements:** Compromised credentials, over-privileged HRMS roles, misconfigured or unauthenticated APIs, leaked filestore signed URLs, phishing of integrator or partner-helpdesk staff, lost/stolen field devices.
- **Affected service(s) / path(s):** \[to be logged].
- **Vulnerability introduction date (and commit/PR if known):** \[to be logged].
- **Relevant system logs:** IAM (`egov-user`), API gateway, service application logs, PostgreSQL audit logs, Kafka consumer logs, AWS/CloudTrail infra logs.

---

## Phase 2: Mitigation

### Guidelines

- **Short-term containment:** Isolate affected IAM and API services, suspend compromised accounts, revoke tokens, stop data exfiltration.
- **Long-term:** Patch IAM/API configs, revoke tokens, enable secure alternative comms, enforce MDM for devices, apply code patches to address vulnerabilities.

### Guidelines specific to this repository

- Emergency code fixes follow the normal contribution path in `CONTRIBUTING.md` — a branch, a PR, and CODEOWNERS review — with the review compressed, **not skipped**. Branch names must still satisfy `.github/workflows/branch-name-validator.yml`.
- Do not disclose the vulnerability detail in the public PR title, branch name, or commit message. Reference the internal incident ID only; publish the detail after the fix is deployed.
- Where a dependency CVE is the root cause, check for an existing Dependabot PR before hand-rolling an upgrade.
- Re-run the Trivy workflow (`workflow_dispatch`) after the patch to confirm the finding clears. Trivy is report-only and will not fail the build — read the GitHub Security tab, don't rely on a green check.
- For Kafka-borne data issues, remember consumers may have already propagated the data to the analytics index and dashboards; containment must cover downstream sinks (Transformer → Elasticsearch → Dashboard Analytics), not just the source service.

### Tasks

- Re-assess severity after containment.
- Check product surfaces: AWS cloud infra, Kubernetes workloads, API gateway, third-party gateways (SMS/notification), field-worker mobile app, citizen-facing health apps.
- Apply classification labels (Incident / Not-actionable).

### Roles & Responsibilities

**Implementation SRE / Implementation Security Analyst**
- Execute short-term containment (disable accounts, revoke tokens).
- Apply monitoring rules for unusual activity.

**Implementation DevOps Engineer**
- Patch IAM/API configs, update firewall/WAF rules.
- Quarantine VMs/containers/pods.
- Support rollback if required.

**Implementation Lead / Security Lead**
- Decide on risk acceptance vs urgent fixes.
- Approve long-term changes (e.g., MDM enforcement, code patch PRs).

**eGov L3 Support**
- Produce the code fix, build, and APK/image release.
- Confirm the fix in a staging environment before production rollout.

### For the Record

- **First mitigation date:** \[log timestamp].
- **Surfaces affected:** Core cloud infra, APIs, IAM, Kafka/analytics pipeline, filestore, BYOD endpoints.
- **Link to mitigation work:** Patch logs, AWS GuardDuty findings, GitHub PR fixing the config/code issue, Trivy re-scan run URL.

---

## Phase 3: Scoping

### Guidelines

- Confirm the incident is real and proceed to measure impact.
- Identify the number of users, organizations, or systems affected.
- Check if data was exfiltrated, modified, or just exposed.
- Establish a timeframe of compromise (start and end).
- Collect key metrics: number of accounts impacted, confidence in completeness.
- Decide if this should be escalated as an official incident requiring notification.
- Scope **per tenant**: HCM is multi-tenant, so quantify affected `tenantId`s as well as individual records. A single compromised state-level credential can span every campaign in that tenant.
- Scope **per campaign**: identify the affected campaign/project, its boundary coverage, and the field teams operating under it.

### Tasks

- Investigate how many users, organizations, or systems are affected.
- Determine if data was exfiltrated, modified, or only exposed.
- Identify the timeframe of compromise (when it began, how long it persisted).
- Collect quantifiable metrics:
  - Number of accounts/users impacted.
  - Number of beneficiary/household records impacted.
  - Number of tenants and campaigns impacted.
  - Level of confidence in completeness (Low / Medium / High).
- Confirm if this should be declared an official incident requiring notification and escalation.

### Roles & Responsibilities

**Implementation SRE / Implementation Security Analyst**
- Collect logs, telemetry, and forensic evidence from impacted systems.
- Run queries to determine which accounts, tenants, and systems are affected.
- Document scope metrics (number of accounts, timeframe, confidence level).

**Implementation Lead / Security Lead**
- Direct the scoping effort, ensure completeness of analysis.
- Validate whether the incident meets thresholds for official declaration.
- Approve escalation to regulators, legal, and notification phase if required.

**Implementation DevOps Engineer**
- Provide system-level evidence (audit logs, DB access logs, API gateway metrics, Kafka offsets).
- Confirm if data was modified, exfiltrated, or just exposed.
- Help estimate impact duration (when compromise began and ended).

**Data Protection Officer (DPO) / Compliance Officer (if applicable)**
- Assess regulatory exposure (GDPR, HIPAA, CERT-In, DPDP Act, state health data rules).
- Advise whether legal notification thresholds are triggered.

**Business Owner / Product Owner**
- Provide business context on the importance of impacted services.
- Estimate operational and citizen-facing impact (campaign delays, service downtime, financial loss).

**Partner Helpdesk (L1/L2)**
- Supply field-side context: which districts/teams were active, what was reported on the ground, device inventory for the affected campaign.

### For the Record

- **Was there a confirmed CIA breach?** (Yes/No; which part of CIA)
- **Number of individual user accounts affected:** \[ ]
- **Number of beneficiary/household records affected:** \[ ]
- **Number of organizations/tenants affected:** \[ ]
- **Campaigns / projects affected:** \[ ]
- **Timeframe of compromise:** Start \[ ], End \[ ]
- **Confidence level in completeness of scoping:** Low / Medium / High
- **Decision:** Escalate to Incident (Yes/No)

---

## Phase 4: Notification

### Guidelines

- Send notifications to stakeholders: internal staff, government partners, affected citizens (if data exposure confirmed).
- SPOC (DPO) coordinates with Legal, PR, and regulators.
- Crisis comms prepared for media inquiries.
- Because end users are citizens reached through government health programmes, citizen notification is routed **through the implementing government department**, not issued directly by eGov, unless the department directs otherwise.
- Partner helpdesk tiers (L0–L2) receive the internal FAQ **before** any public statement, so field teams are not answering citizens from a news report.

### Tasks

- Decide whether to notify (mandatory if CIA breach confirmed).
- Draft notification (citizens, government departments, regulators).
- Leadership escalation (CTO + board brief).
- Legal & PR approval.
- Prepare FAQ for internal staff & support team (L0–L3).
- Publish supporting comms if applicable (blog, changelog, release notes, gov't circulars).
- Once the fix is deployed and the window for exploitation has closed, publish the security advisory / release note for this repository.

### Roles & Responsibilities

**SPOC (DPO)**
- Coordinate external communication (regulators, govt partners, media).
- Approve citizen notifications.

**Legal & PR**
- Draft legal notices, regulator reports, media statements.
- Approve language for public and citizen communication.

**Support/Operations Team**
- Update FAQs for staff and end-users.
- Handle inbound queries from citizens/partners.

### For the Record

- **Notification decision:** \[Yes/No].
- **Date & time of notification:** \[to be logged].
- **Channels:** Email, WhatsApp, direct calls, government circular, media release.
- **Number of notifications sent:** \[to be logged].
- **Links:** Notification content draft, regulator reports, public statement, repository advisory/release note.

---

## Operational Reference

### Severity Assignment Guide

**Criticality of Systems Affected**
- Services critical to citizen welfare → higher severity. In this repository: Individual, Household, Project, Referral Management, Beneficiary IDGen.
- Non-critical systems → lower severity.

**Scope of Impact**
- Number of users, organizations, or partners affected.
- Localized issue (single user, single boundary) vs widespread (entire state tenant / all campaigns).

**Exploitability & Ease of Attack**
- Publicly known exploit, low barrier (e.g., no authentication required) → higher severity.
- Requires insider access, complex timing, or rare configurations → lower severity.

**Regulatory & Legal Exposure**
- Breaches involving PII/PHI often trigger mandatory disclosure (GDPR, HIPAA, CERT-In, DPDP Act). This escalates severity.

**Potential for Lateral Movement**
- If an attacker can pivot into other systems (e.g., from the SMS/notification gateway into the core citizen DB, or from Transformer into the full analytics index), raise severity.

**Business & Operational Disruption**
- Service downtime affecting public governance, financial loss, or halted citizen services → higher severity. A campaign that cannot register beneficiaries is a halted citizen service.

**Detection vs Exploitation**
- If only a vulnerability exists (no active exploitation) → moderate severity.
- If there's active exploitation / data exfiltration → critical severity.

### Mitigation Methods Catalog

1. **Access & Identity Controls**
   - Revoke or rotate compromised credentials (passwords, API keys, OAuth tokens).
   - Enforce password resets or MFA enrollment.
   - Disable suspicious user accounts or sessions in `egov-user` / HRMS.
   - Apply least privilege policies temporarily (restrict admin roles).

2. **Network & Infrastructure Controls**
   - Block malicious IPs, domains, or geographies at the firewall / WAF.
   - Segment affected networks or services (quarantine zones).
   - Shut down or isolate compromised servers, VMs, pods, or containers.
   - Throttle traffic to reduce DDoS impact.

3. **Application & Platform Controls**
   - Disable vulnerable features/modules (e.g., bulk Excel ingestion, file upload, a specific API).
   - Apply hotfixes, config changes, or temporary patches.
   - Increase rate limits and validation checks at the API gateway.
   - Pause Kafka consumers to stop propagation of tainted records downstream.

4. **Endpoint & Device Controls**
   - Quarantine infected BYOD or corporate devices.
   - Remote-wipe or deregister lost/stolen field devices; force re-authentication of the field app.

5. **Data Protection Measures**
   - Stop ongoing data exfiltration (block S3/MinIO/filestore downloads, revoke signed URLs).
   - Restrict access to critical DBs to only essential accounts/services.
   - Verify `egov-enc-service` encryption is intact for Individual, Worker Registry, Referral Management, and Health Notification data; rotate encryption keys if key compromise is suspected.

6. **Monitoring & Logging Enhancements**
   - Enable additional logging levels for affected systems.
   - Deploy temporary alerts for unusual access patterns.
   - Preserve forensic evidence (don't wipe logs); snapshot affected volumes before remediation.

7. **Third-Party & Vendor Coordination**
   - Disable or limit integrations with compromised partners (SMS gateways, payment APIs).
   - Coordinate with vendors for emergency patching.
   - Notify cloud providers (AWS, GCP, Azure) if an infrastructure-level breach is suspected.

8. **Supply Chain & Build Pipeline Controls**
   - Rotate CI/CD secrets and registry credentials if a build system compromise is suspected.
   - Re-run Trivy image scans and verify published image digests.
   - Review recent merges to `master` and the GitHub audit log for unexpected commits or workflow changes.

---

## Post-Incident Review

Within **10 working days** of closing an incident, the Implementation Security Lead convenes a blameless post-incident review covering:

- Timeline from first signal to containment to resolution.
- Root cause, and whether existing controls (Trivy, Codacy, Dependabot, Scorecard, code review) could have caught it earlier.
- Gaps in detection, logging, or runbooks.
- Action items with named owners and due dates, tracked as issues/tickets.
- Whether this IRP needs updating — if so, raise a PR against this file.

---

*Maintained by the eGov Foundation security and platform teams. Raise changes as a pull request against `master` following `CONTRIBUTING.md`.*
