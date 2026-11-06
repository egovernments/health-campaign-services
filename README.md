# Health Campaign Services

Backend microservices for the **DIGIT Health Campaign Management (HCM)** platform. This repository contains the services that power campaign setup, beneficiary registration, inventory and stock tracking, field-level workflows, and analytics for health campaigns run on the DIGIT platform.

This is a public repository maintained by the [eGovernments Foundation](https://github.com/egovernments) and is released under the MIT License.

## Table of Contents

- [Overview](#overview)
- [Repository Structure](#repository-structure)
- [Key Components](#key-components)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [CI/CD and Deployment](#cicd-and-deployment)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Code of Conduct](#code-of-conduct)
- [License](#license)

## Overview

Health Campaign Services is a set of Java/Spring Boot microservices that sit on top of the DIGIT core platform. The core platform provides shared capabilities such as user management, access control, master data (MDMS), file storage, encryption, and the persister and indexer pipelines. This repository adds the health-specific services that model campaigns, households, individuals, facilities, products, and the operational workflows around them.

Services in this repository communicate with DIGIT core services over REST APIs and use PostgreSQL for persistence. Their behaviour is largely driven by configuration (master data, workflow definitions, persister and indexer configs), which is maintained in companion repositories rather than here.

## Repository Structure

| Path | Purpose |
| --- | --- |
| `health-services/` | Health domain microservices (registries, campaign, and operational services). |
| `core-services/` | Supporting core-level services used alongside the health services. |
| `analytics/auth-proxy/` | Authentication proxy used by analytics and dashboard components. |
| `utilities/airflow-trigger-service/` | Service that triggers Apache Airflow DAGs for data pipelines. |
| `build/` | Shared build configuration, including the build config used by the CI pipeline. |
| `docs/` | Supporting documentation, including API specifications under `docs/health-api-specs/`. |
| `.github/workflows/` | GitHub Actions workflows for build and validation. |
| `Jenkinsfile` | Jenkins pipeline definition used for automated builds and deployments. |
| `.vscode/` | Editor settings for contributors. |

Each service directory is intended to be a self-contained Maven or Spring Boot project. Check the individual service folder for its own build and run instructions.

## Key Components

The health services cover the main entities and workflows of a campaign. Based on the DIGIT HCM documentation, they include:

- **Registries**: Individual, Household, Product, Facility, and Referral registries, each with its own API and data model.
- **Project Factory (Campaign Manager)**: Campaign creation and update APIs, data management APIs, and boundary-based campaign setup.
- **Plan Service**: Microplanning, including filter, preview-aggregate, and upload configuration.
- **Muster Roll**: Worker attendance and payment rolls.
- **Expense Calculator**: Calculation of campaign-related expenses.
- **Stock and Inventory**: Tracking of stock movements and product inventory across facilities and warehouses.
- **Complaints**: Complaint intake and handling, built on the PGR pattern from DIGIT.
- **HRMS and User Management**: Staff records and role assignment for campaign workers.

Depending on the release, the exact set of services deployed may differ. Refer to the service build updates documentation for the list used in a given version.

## Getting Started

### Prerequisites

- Java (check the `pom.xml` in the service you are working on for the required version)
- Maven
- PostgreSQL
- Docker (for building images and running dependencies locally)
- Access to a DIGIT core stack, or local equivalents of the core services the service depends on (MDMS, user, access control, file store, and so on)

### Building a Service

Each service is built independently. From the service directory:

```bash
mvn clean install
```

To build a Docker image, use the Dockerfile in the service's `build/` directory. The image name and build context are defined in `build/build-config.yml`.

### Running Locally

1. Create a PostgreSQL database for the service.
2. Set the database connection and DIGIT core service URLs in the service's `application.properties` (or the equivalent environment variables).
3. Start the service with `mvn spring-boot:run` or by running the packaged JAR.

Database migrations use Flyway and run on startup.

## Configuration

Most runtime behaviour is set through configuration rather than code:

- **MDMS (Master Data Management Service)**: Master data such as boundary hierarchies, ID formats, roles, and map configs. Health master data is maintained in the `health-campaign-mdms` repository.
- **Access control**: Role-to-action mappings (`roleactions.json`) and action definitions (`actions-test.json`) control which roles can call which endpoints.
- **Persister and indexer configs**: YAML files that define how service data is persisted and indexed. These are maintained in the `health-campaign-config` repository.
- **Workflow configs**: Business service definitions for approval and state-transition flows.
- **Secrets and environment values**: Database credentials and core service endpoints are supplied through environment-specific secret files and must not be committed to this repository.

## CI/CD and Deployment

- **GitHub Actions** (`.github/workflows/`) runs build and validation checks on pull requests.
- **Jenkins** (`Jenkinsfile`) builds Docker images and triggers deployments for registered services.
- **Helm charts** for deploying services to Kubernetes are maintained in the `DIGIT-DevOps` repository, under `deploy-as-code/helm/charts/health-services/`.

The deployment guide for HCM is available on the DIGIT documentation site (see below).

## Documentation

- [DIGIT Health documentation](https://health.digit.org): product guides, configuration, and release notes.
- [DIGIT developer documentation](https://core.digit.org): core platform services and architecture.
- `docs/health-api-specs/`: OpenAPI (Swagger) contracts for the health services, including the registries and complaints APIs.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. In brief:

1. Fork the repository and create a feature branch from `master`.
2. Make your changes and include tests where applicable.
3. Make sure the service builds and existing tests pass.
4. Open a pull request with a clear description of the change and its motivation.

Code ownership is defined in [CODEOWNERS](CODEOWNERS). Pull requests are reviewed by the owners of the affected paths.

## Code of Conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.