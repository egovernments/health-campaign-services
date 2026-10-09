# Local Setup

To set up the Resource Estimation Service in your local system, clone the [Health Campaign Services repository](https://github.com/egovernments/health-campaign-services.git).

## Dependencies

- [x] Postgres DB
- [ ] Redis
- [ ] Elasticsearch
- [x] Kafka
  - [x] Consumer
  - [x] Producer


## Running Locally

### Local setup
1. To set up the Resource Estimation Service in your local system, clone the [Health Campaign Services repository](https://github.com/egovernments/health-campaign-services.git).
2. Install GIT.
	[For Windows](https://git-scm.com/download/win).
	[For Linux](https://www.digitalocean.com/community/tutorials/how-to-install-git-on-ubuntu-18-04-quickstart).
2. Install JDK version 17 or above.
	[For windows](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html).
	[For Linux](https://javahelps.com/install-oracle-jdk-17-on-linux).
3. Install maven locally and configure environment variables.
4. Install Kafka (version 3.2.0 which is the latest version) - To install and run Kafka locally, follow the following links -
	[Kafka for windows](https://dzone.com/articles/running-apache-kafka-on-windows-os) or [Kafka for Linux](https://tecadmin.net/install-apache-kafka-ubuntu/)
5. Install Postman - To install Postman, follow the following links -
	[Postman for windows](https://www.postman.com/downloads/)
6. Install Kubectl - Kubectl is the tool that we use to interact with services deployed on our sandbox environment -
	[kubectl for windows](https://core.digit.org/guides/operations-guide/working-with-kubernetes/installation-of-kubectl)
    [kubectl for Linux](https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/)
7. Install aws-iam-authenticator - [if the DIGIT development environment is in AWS](https://docs.aws.amazon.com/eks/latest/userguide/install-aws-iam-authenticator.html)
8. Install PostgreSQL v14 locally.
4. Also update DB config values as per your local system config.
5. Update all dependency service host either on any unified-env or port-forward.
6. Run spring boot main class

> Note: After running the above steps, if you encounter a Kafka error, ensure that both Kafka and Zookeeper services are actively running. For connection errors with other microservices, verify the correctness of the URL in the external mapping of the data configuration or consider port-forwarding the problematic service for direct access.