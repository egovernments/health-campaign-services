# Local Setup

To setup the Project service in your local system, clone the [Health campaign services](https://github.com/egovernments/health-campaign-services).

## Dependencies

### Infra Dependency

- [X] Postgres DB
- [X] Redis
- [X] Elasticsearch
- [X] Kafka
    - [X] Consumer
    - [X] Producer


## Running Locally

You can use docker-compose file to get started with these dependencies. Download docker-compose.yml from [here](../libraries/docker-compose.yml)

Use the following command to start containers

```
cd path/to/docker-compose.yml file

docker-compose up -d
```

To run it locally this service require port forwarding for idgen service, facility service, household service and product service.

Directly run the application.