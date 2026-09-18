# Security Policy

## Supported Versions

Security fixes are applied to the `master` branch. Deployments tracking older
release tags should upgrade to the latest `master` build to receive fixes.

| Version           | Supported          |
| ----------------- | ------------------ |
| `master` (latest) | :white_check_mark: |
| Older tags        | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
pull requests, or discussions.**

Report a suspected vulnerability in one of the following ways:

1. **GitHub Private Vulnerability Reporting** (preferred) — open the
   [Security tab](https://github.com/egovernments/health-campaign-services/security/advisories/new)
   of this repository and submit a private advisory.
2. **Email** — send details to **security@egovernments.org**.

Please include as much of the following as you can:

- The affected service or module (for example `health-services/project-factory`)
  and the commit, tag, or image digest you tested.
- The type of issue (for example authentication bypass, SQL injection,
  deserialization, SSRF, privilege escalation).
- Step-by-step reproduction instructions, including any proof-of-concept
  request, payload, or configuration required.
- The impact you believe the issue has, and any suggested mitigation.

Please do not include real personal, patient, or beneficiary data in your
report. Redact or synthesise any sample data.

## Our Commitment

- We will acknowledge your report within **3 business days**.
- We will provide an initial assessment, including a severity rating and whether
  we accept the report, within **10 business days**.
- We will keep you informed of remediation progress at least every **14 days**
  until the issue is resolved.
- We aim to release fixes for critical and high severity issues within **30
  days** of triage, and for medium and low severity issues in a subsequent
  scheduled release.

## Coordinated Disclosure

We follow coordinated disclosure. Please give us a reasonable opportunity to
remediate before any public disclosure — we ask for **90 days** from the date we
acknowledge your report, or until a fix ships, whichever comes first. We are
happy to credit reporters in the resulting advisory unless you prefer to remain
anonymous.

## Scope

In scope: source code, container build definitions, and CI/CD workflow
configuration in this repository.

Out of scope: findings against third-party hosted deployments operated by
adopters of this software, denial-of-service testing against any running
environment, social engineering, and automated scanner output submitted without
a demonstrated, reproducible impact.

## Security Practices in This Repository

This repository runs the following automated checks:

- **OpenSSF Scorecard** (`.github/workflows/scorecard.yml`) — supply-chain
  posture, published to the OpenSSF REST API.
- **CodeQL** (`.github/workflows/codeql.yml`) — static analysis (SAST) for Java
  and JavaScript/TypeScript on every push and pull request to `master`.
- **Codacy** (`.github/workflows/codacy.yml`) — additional static analysis,
  results uploaded to GitHub code scanning.
- **Dependabot** (`.github/dependabot.yml`) — automated dependency and GitHub
  Actions updates.

All GitHub Actions are pinned to full-length commit SHAs, and workflows declare
least-privilege `permissions` blocks.
