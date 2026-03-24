# Local Setup

To set up the Plan Service in your local system, clone the [Health Campaign Services repository](https://github.com/egovernments/health-campaign-services.git).

## Dependencies

- [x] Postgres DB
- [ ] Redis
- [ ] Elasticsearch
- [x] Kafka
  - [x] Consumer
  - [x] Producer


## Running Locally

### Local setup
1. To set up the Plan Service in your local system, clone the [Health Campaign Services repository](https://github.com/egovernments/health-campaign-services.git).
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
9. Also update DB config values as per your local system config.
10. Update the host settings for all dependency services, either on any unified environment or by port-forwarding.
11. Run spring boot main class

> Note: After running the above, if a Kafka error occurs, ensure that Kafka and Zookeeper are running in the background. If a connection error with another microservice occurs, ensure that the URL mentioned in the external mapping of the data config is correct, or you can port-forward that particular service.
\ No newline at end of file